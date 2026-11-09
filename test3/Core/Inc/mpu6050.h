/**
  ******************************************************************************
  * @file           : mpu6050.h
  * @brief          : MPU6050 (GY-521) Gyroscope/Accelerometer I2C Driver
  * @author         : Sapuangin Telemetry Team
  * @description    : HAL-based driver for reading accelerometer, gyroscope,
  *                   and temperature data from MPU6050 via I2C.
  *                   Includes complementary filter for stable angle output.
  ******************************************************************************
  */

#ifndef __MPU6050_H
#define __MPU6050_H

#include "stm32f4xx_hal.h"
#include <math.h>

/* ======================== MPU6050 I2C ADDRESS ======================== */
/* AD0 pin LOW  = 0x68 (default)
 * AD0 pin HIGH = 0x69
 * HAL requires 8-bit address (shifted left by 1) */
#define MPU6050_ADDR           (0x68 << 1)

/* ======================== MPU6050 REGISTERS ========================== */
#define MPU6050_REG_SMPLRT_DIV    0x19  /* Sample Rate Divider */
#define MPU6050_REG_CONFIG        0x1A  /* Configuration (DLPF) */
#define MPU6050_REG_GYRO_CONFIG   0x1B  /* Gyroscope Configuration */
#define MPU6050_REG_ACCEL_CONFIG  0x1C  /* Accelerometer Configuration */
#define MPU6050_REG_ACCEL_XOUT_H  0x3B  /* Accel X-axis High byte */
#define MPU6050_REG_TEMP_OUT_H    0x41  /* Temperature High byte */
#define MPU6050_REG_GYRO_XOUT_H   0x43  /* Gyro X-axis High byte */
#define MPU6050_REG_PWR_MGMT_1    0x6B  /* Power Management 1 */
#define MPU6050_REG_WHO_AM_I      0x75  /* Who Am I (should return 0x68) */

/* ======================== CONFIGURATION VALUES ======================= */
/* Accelerometer Full Scale Range */
#define MPU6050_ACCEL_FS_2G       0x00  /* ±2g  -> 16384 LSB/g */
#define MPU6050_ACCEL_FS_4G       0x08  /* ±4g  -> 8192  LSB/g */
#define MPU6050_ACCEL_FS_8G       0x10  /* ±8g  -> 4096  LSB/g */
#define MPU6050_ACCEL_FS_16G      0x18  /* ±16g -> 2048  LSB/g */

/* Gyroscope Full Scale Range */
#define MPU6050_GYRO_FS_250       0x00  /* ±250°/s  -> 131   LSB/°/s */
#define MPU6050_GYRO_FS_500       0x08  /* ±500°/s  -> 65.5  LSB/°/s */
#define MPU6050_GYRO_FS_1000      0x10  /* ±1000°/s -> 32.8  LSB/°/s */
#define MPU6050_GYRO_FS_2000      0x18  /* ±2000°/s -> 16.4  LSB/°/s */

/* Complementary Filter coefficient
 * Higher value = trust gyroscope more (smoother but drifts)
 * Lower value  = trust accelerometer more (noisy but no drift) */
#define MPU6050_COMP_FILTER_ALPHA 0.96f

/* Radians to Degrees conversion */
#define RAD_TO_DEG                57.295779513f  /* 180/PI */

/* ======================== DATA STRUCTURES ============================ */

/**
 * @brief MPU6050 initialization status
 */
typedef enum {
    MPU6050_OK       = 0,
    MPU6050_ERROR    = 1,
    MPU6050_TIMEOUT  = 2
} MPU6050_Status;

/**
 * @brief MPU6050 data structure
 *        Stores raw sensor data and calculated angles
 */
typedef struct {
    /* Raw accelerometer values (in g) */
    float Accel_X;
    float Accel_Y;
    float Accel_Z;

    /* Raw gyroscope values (in °/s) */
    float Gyro_X;
    float Gyro_Y;
    float Gyro_Z;

    /* Temperature (in °C) */
    float Temperature;

    /* Calculated angles from complementary filter (in degrees) */
    float Pitch;    /* Rotation around X-axis (nose up/down) */
    float Roll;     /* Rotation around Y-axis (tilt left/right) */
    float Yaw;      /* Rotation around Z-axis (heading) */

    /* Gyroscope offsets for calibration */
    float Gyro_X_Offset;
    float Gyro_Y_Offset;
    float Gyro_Z_Offset;

    /* Timestamp for dt calculation */
    uint32_t lastTick;
} MPU6050_t;

/* ======================== FUNCTION PROTOTYPES ======================== */

/**
 * @brief  Initialize MPU6050 sensor
 * @param  hi2c: Pointer to I2C handle
 * @param  mpu: Pointer to MPU6050 data structure
 * @retval MPU6050_Status
 */
MPU6050_Status MPU6050_Init(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu);

/**
 * @brief  Calibrate gyroscope by averaging samples at rest
 * @param  hi2c: Pointer to I2C handle
 * @param  mpu: Pointer to MPU6050 data structure
 * @note   Sensor MUST be stationary during calibration!
 */
void MPU6050_Calibrate(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu);

/**
 * @brief  Read all sensor data (accel + gyro + temp) and calculate angles
 * @param  hi2c: Pointer to I2C handle
 * @param  mpu: Pointer to MPU6050 data structure
 * @retval MPU6050_Status
 */
MPU6050_Status MPU6050_Read_All(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu);

/**
 * @brief  Read only accelerometer data
 * @param  hi2c: Pointer to I2C handle
 * @param  mpu: Pointer to MPU6050 data structure
 * @retval MPU6050_Status
 */
MPU6050_Status MPU6050_Read_Accel(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu);

/**
 * @brief  Read only gyroscope data
 * @param  hi2c: Pointer to I2C handle
 * @param  mpu: Pointer to MPU6050 data structure
 * @retval MPU6050_Status
 */
MPU6050_Status MPU6050_Read_Gyro(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu);

/**
 * @brief  Read temperature
 * @param  hi2c: Pointer to I2C handle
 * @param  mpu: Pointer to MPU6050 data structure
 * @retval MPU6050_Status
 */
MPU6050_Status MPU6050_Read_Temp(I2C_HandleTypeDef *hi2c, MPU6050_t *mpu);

#endif /* __MPU6050_H */
