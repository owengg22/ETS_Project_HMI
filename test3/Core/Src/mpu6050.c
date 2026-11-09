/**
  ******************************************************************************
  * @file           : mpu6050.c
  * @brief          : MPU6050 (GY-521) I2C Driver Implementation
  * @author         : Sapuangin Telemetry Team
  * @description    : Reads accelerometer, gyroscope, and temperature via I2C.
  *                   Uses complementary filter for stable Pitch/Roll/Yaw.
  ******************************************************************************
  */

#include "mpu6050.h"
#include <string.h>

/* ===================== INTERNAL CONSTANTS ================================ */

/* Sensitivity scale factors based on configured Full Scale Range */
static const float ACCEL_SENSITIVITY = 16384.0f;  /* ±2g  -> 16384 LSB/g */
static const float GYRO_SENSITIVITY  = 131.0f;    /* ±250°/s -> 131 LSB/°/s */

/* Number of samples for gyroscope calibration */
#define CALIBRATION_SAMPLES  2000

/* I2C timeout in milliseconds */
#define I2C_TIMEOUT          100

/* ===================== HELPER FUNCTIONS ================================== */

/**
 * @brief Write a single byte to MPU6050 register
 */
static MPU6050_Status MPU6050_WriteReg(I2C_HandleTypeDef *hi2c, uint8_t reg, uint8_t data)
{
    if (HAL_I2C_Mem_Write(hi2c, MPU6050_ADDR, reg, I2C_MEMADD_SIZE_8BIT,
                          &data, 1, I2C_TIMEOUT) != HAL_OK)
    {
        return MPU6050_ERROR;
    }
    return MPU6050_OK;
}

/**
 * @brief Read multiple bytes from MPU6050 registers
 */
static MPU6050_Status MPU6050_ReadRegs(I2C_HandleTypeDef *hi2c, uint8_t reg,
                                        uint8_t *data, uint16_t len)
{
    if (HAL_I2C_Mem_Read(hi2c, MPU6050_ADDR, reg, I2C_MEMADD_SIZE_8BIT,
                         data, len, I2C_TIMEOUT) != HAL_OK)
    {
        return MPU6050_ERROR;
    }
    return MPU6050_OK;
}

/* ===================== PUBLIC FUNCTIONS =================================== */

/**
 * @brief  Initialize MPU6050 sensor
 *
 * Sequence:
 *   1. Check WHO_AM_I register to verify sensor is connected
 *   2. Wake up sensor (clear SLEEP bit in PWR_MGMT_1)
 *   3. Set sample rate divider
 *   4. Configure DLPF (Digital Low Pass Filter)
 *   5. Set accelerometer range to ±2g
 *   6. Set gyroscope range to ±250°/s
 *   7. Calibrate gyroscope offsets
 */
MPU6050_Status MPU6050_Init(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu)
{
    uint8_t whoami = 0;
    uint8_t check;

    /* Clear the data structure */
    memset(mpu, 0, sizeof(MPU6050_t));

    /* Step 1: Verify device identity via WHO_AM_I register */
    if (MPU6050_ReadRegs(hi2c, MPU6050_REG_WHO_AM_I, &whoami, 1) != MPU6050_OK)
    {
        return MPU6050_ERROR;  /* I2C communication failed */
    }

    /* WHO_AM_I should return 0x68 (MPU6050) or 0x72 (MPU6050 clone) */
    if (whoami != 0x68 && whoami != 0x72 && whoami != 0x98)
    {
        return MPU6050_ERROR;  /* Wrong device */
    }

    /* Step 2: Wake up sensor — Write 0x00 to PWR_MGMT_1 (clears SLEEP bit)
     * Also selects internal 8MHz oscillator as clock source */
    if (MPU6050_WriteReg(hi2c, MPU6050_REG_PWR_MGMT_1, 0x00) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }
    HAL_Delay(100);  /* Wait for sensor to stabilize after wake-up */

    /* Step 3: Set sample rate divider
     * Sample Rate = Gyroscope Output Rate / (1 + SMPLRT_DIV)
     * With DLPF enabled: Gyroscope Output Rate = 1kHz
     * SMPLRT_DIV = 4 -> Sample Rate = 1000 / (1+4) = 200 Hz */
    if (MPU6050_WriteReg(hi2c, MPU6050_REG_SMPLRT_DIV, 0x04) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    /* Step 4: Configure Digital Low Pass Filter (DLPF)
     * DLPF_CFG = 3 -> Accel BW=44Hz, Gyro BW=42Hz
     * This reduces noise while maintaining acceptable latency */
    if (MPU6050_WriteReg(hi2c, MPU6050_REG_CONFIG, 0x03) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    /* Step 5: Set accelerometer full scale range to ±2g */
    if (MPU6050_WriteReg(hi2c, MPU6050_REG_ACCEL_CONFIG, MPU6050_ACCEL_FS_2G) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    /* Step 6: Set gyroscope full scale range to ±250°/s */
    if (MPU6050_WriteReg(hi2c, MPU6050_REG_GYRO_CONFIG, MPU6050_GYRO_FS_250) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    /* Verify configuration was written correctly */
    if (MPU6050_ReadRegs(hi2c, MPU6050_REG_PWR_MGMT_1, &check, 1) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    /* Step 7: Calibrate gyroscope */
    MPU6050_Calibrate(hi2c, mpu);

    /* Initialize timestamp */
    mpu->lastTick = HAL_GetTick();

    return MPU6050_OK;
}

/**
 * @brief  Calibrate gyroscope by averaging readings while stationary
 *
 * Takes CALIBRATION_SAMPLES samples and computes the average offset
 * for each gyroscope axis. These offsets are subtracted from future
 * readings to eliminate bias/drift at rest.
 *
 * IMPORTANT: Sensor must be completely still during calibration!
 */
void MPU6050_Calibrate(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu)
{
    float sum_x = 0.0f, sum_y = 0.0f, sum_z = 0.0f;
    uint8_t buf[6];
    int16_t raw_x, raw_y, raw_z;

    /* Reset offsets before calibration */
    mpu->Gyro_X_Offset = 0.0f;
    mpu->Gyro_Y_Offset = 0.0f;
    mpu->Gyro_Z_Offset = 0.0f;

    for (int i = 0; i < CALIBRATION_SAMPLES; i++)
    {
        /* Read 6 bytes of gyroscope data starting from GYRO_XOUT_H */
        if (MPU6050_ReadRegs(hi2c, MPU6050_REG_GYRO_XOUT_H, buf, 6) == MPU6050_OK)
        {
            /* Combine high and low bytes (MSB first) */
            raw_x = (int16_t)(buf[0] << 8 | buf[1]);
            raw_y = (int16_t)(buf[2] << 8 | buf[3]);
            raw_z = (int16_t)(buf[4] << 8 | buf[5]);

            sum_x += (float)raw_x / GYRO_SENSITIVITY;
            sum_y += (float)raw_y / GYRO_SENSITIVITY;
            sum_z += (float)raw_z / GYRO_SENSITIVITY;
        }
        HAL_Delay(1);  /* Small delay between samples */
    }

    /* Compute average offset */
    mpu->Gyro_X_Offset = sum_x / (float)CALIBRATION_SAMPLES;
    mpu->Gyro_Y_Offset = sum_y / (float)CALIBRATION_SAMPLES;
    mpu->Gyro_Z_Offset = sum_z / (float)CALIBRATION_SAMPLES;
}

/**
 * @brief  Read all sensor data and calculate angles using complementary filter
 *
 * Reads 14 bytes in a single burst (accel[6] + temp[2] + gyro[6])
 * starting from register 0x3B. This is the most efficient way to
 * read all data from MPU6050.
 *
 * Angle calculation:
 *   - Pitch/Roll from accelerometer: atan2() (absolute, but noisy)
 *   - Pitch/Roll from gyroscope: integration (smooth, but drifts)
 *   - Complementary filter combines both for stable output
 *   - Yaw: gyroscope integration only (no magnetometer for absolute heading)
 */
MPU6050_Status MPU6050_Read_All(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu)
{
    uint8_t buf[14];
    int16_t raw_accel_x, raw_accel_y, raw_accel_z;
    int16_t raw_temp;
    int16_t raw_gyro_x, raw_gyro_y, raw_gyro_z;
    float accel_pitch, accel_roll;
    float dt;
    uint32_t currentTick;

    /* Read 14 bytes starting from ACCEL_XOUT_H (0x3B)
     * Layout: AccelX[2] AccelY[2] AccelZ[2] Temp[2] GyroX[2] GyroY[2] GyroZ[2] */
    if (MPU6050_ReadRegs(hi2c, MPU6050_REG_ACCEL_XOUT_H, buf, 14) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    /* Parse raw values — each value is 16-bit signed, MSB first */
    raw_accel_x = (int16_t)(buf[0]  << 8 | buf[1]);
    raw_accel_y = (int16_t)(buf[2]  << 8 | buf[3]);
    raw_accel_z = (int16_t)(buf[4]  << 8 | buf[5]);
    raw_temp    = (int16_t)(buf[6]  << 8 | buf[7]);
    raw_gyro_x  = (int16_t)(buf[8]  << 8 | buf[9]);
    raw_gyro_y  = (int16_t)(buf[10] << 8 | buf[11]);
    raw_gyro_z  = (int16_t)(buf[12] << 8 | buf[13]);

    /* Convert to physical units */
    mpu->Accel_X = (float)raw_accel_x / ACCEL_SENSITIVITY;
    mpu->Accel_Y = (float)raw_accel_y / ACCEL_SENSITIVITY;
    mpu->Accel_Z = (float)raw_accel_z / ACCEL_SENSITIVITY;

    /* Gyroscope: subtract calibration offsets */
    mpu->Gyro_X = ((float)raw_gyro_x / GYRO_SENSITIVITY) - mpu->Gyro_X_Offset;
    mpu->Gyro_Y = ((float)raw_gyro_y / GYRO_SENSITIVITY) - mpu->Gyro_Y_Offset;
    mpu->Gyro_Z = ((float)raw_gyro_z / GYRO_SENSITIVITY) - mpu->Gyro_Z_Offset;

    /* Temperature: formula from MPU6050 datasheet
     * Temp_degC = (raw_temp / 340.0) + 36.53 */
    mpu->Temperature = ((float)raw_temp / 340.0f) + 36.53f;

    /* =================== ANGLE CALCULATION ========================== */

    /* Calculate dt (delta time) in seconds */
    currentTick = HAL_GetTick();
    dt = (float)(currentTick - mpu->lastTick) / 1000.0f;
    mpu->lastTick = currentTick;

    /* Clamp dt to avoid spikes on first read or overflow */
    if (dt <= 0.0f || dt > 1.0f) dt = 0.005f;

    /* Pitch and Roll from accelerometer (absolute reference)
     * atan2 gives angle in radians, convert to degrees
     *
     * Pitch = rotation around X-axis = depends on Y and Z accel
     * Roll  = rotation around Y-axis = depends on X and Z accel */
    accel_pitch = atan2f(mpu->Accel_Y,
                         sqrtf(mpu->Accel_X * mpu->Accel_X +
                               mpu->Accel_Z * mpu->Accel_Z)) * RAD_TO_DEG;

    accel_roll = atan2f(-mpu->Accel_X,
                        sqrtf(mpu->Accel_Y * mpu->Accel_Y +
                              mpu->Accel_Z * mpu->Accel_Z)) * RAD_TO_DEG;

    /* Complementary filter:
     *   angle = α * (angle + gyro * dt) + (1-α) * accel_angle
     *
     * The gyroscope provides smooth, responsive angle changes but drifts over time.
     * The accelerometer provides absolute reference but is noisy.
     * The complementary filter fuses both: mostly trusts gyro for short-term,
     * and corrects drift using accelerometer for long-term. */
    mpu->Pitch = MPU6050_COMP_FILTER_ALPHA * (mpu->Pitch + mpu->Gyro_X * dt)
               + (1.0f - MPU6050_COMP_FILTER_ALPHA) * accel_pitch;

    mpu->Roll  = MPU6050_COMP_FILTER_ALPHA * (mpu->Roll + mpu->Gyro_Y * dt)
               + (1.0f - MPU6050_COMP_FILTER_ALPHA) * accel_roll;

    /* Yaw: pure gyroscope integration (no accelerometer reference for heading)
     * Note: this WILL drift over time without a magnetometer */
    mpu->Yaw += mpu->Gyro_Z * dt;

    return MPU6050_OK;
}

/**
 * @brief  Read accelerometer data only
 */
MPU6050_Status MPU6050_Read_Accel(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu)
{
    uint8_t buf[6];
    int16_t raw_x, raw_y, raw_z;

    if (MPU6050_ReadRegs(hi2c, MPU6050_REG_ACCEL_XOUT_H, buf, 6) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    raw_x = (int16_t)(buf[0] << 8 | buf[1]);
    raw_y = (int16_t)(buf[2] << 8 | buf[3]);
    raw_z = (int16_t)(buf[4] << 8 | buf[5]);

    mpu->Accel_X = (float)raw_x / ACCEL_SENSITIVITY;
    mpu->Accel_Y = (float)raw_y / ACCEL_SENSITIVITY;
    mpu->Accel_Z = (float)raw_z / ACCEL_SENSITIVITY;

    return MPU6050_OK;
}

/**
 * @brief  Read gyroscope data only
 */
MPU6050_Status MPU6050_Read_Gyro(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu)
{
    uint8_t buf[6];
    int16_t raw_x, raw_y, raw_z;

    if (MPU6050_ReadRegs(hi2c, MPU6050_REG_GYRO_XOUT_H, buf, 6) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    raw_x = (int16_t)(buf[0] << 8 | buf[1]);
    raw_y = (int16_t)(buf[2] << 8 | buf[3]);
    raw_z = (int16_t)(buf[4] << 8 | buf[5]);

    mpu->Gyro_X = ((float)raw_x / GYRO_SENSITIVITY) - mpu->Gyro_X_Offset;
    mpu->Gyro_Y = ((float)raw_y / GYRO_SENSITIVITY) - mpu->Gyro_Y_Offset;
    mpu->Gyro_Z = ((float)raw_z / GYRO_SENSITIVITY) - mpu->Gyro_Z_Offset;

    return MPU6050_OK;
}

/**
 * @brief  Read temperature only
 */
MPU6050_Status MPU6050_Read_Temp(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu)
{
    uint8_t buf[2];
    int16_t raw_temp;

    if (MPU6050_ReadRegs(hi2c, MPU6050_REG_TEMP_OUT_H, buf, 2) != MPU6050_OK)
    {
        return MPU6050_ERROR;
    }

    raw_temp = (int16_t)(buf[0] << 8 | buf[1]);
    mpu->Temperature = ((float)raw_temp / 340.0f) + 36.53f;

    return MPU6050_OK;
}
