/**
  ******************************************************************************
  * @file           : hmc5883l.c
  * @brief          : HMC5883L / QMC5883L Magnetometer Driver Implementation
  * @author         : Sapuangin Telemetry Team
  * @description    : Auto-detects HMC5883L (0x1E) or QMC5883L (0x0D) on GY-87,
  *                   reads 3-axis magnetic field, and calculates compass heading.
  ******************************************************************************
  */

#include "hmc5883l.h"
#include <string.h>

#define I2C_TIMEOUT 100

/**
 * @brief Initialize Magnetometer (Auto-detects HMC5883L or QMC5883L)
 */
Mag_Status HMC5883L_Init(I2C_HandleTypeDef *hi2c, HMC5883L_t *mag)
{
    uint8_t data;
    memset(mag, 0, sizeof(HMC5883L_t));

    /* Try HMC5883L first (0x1E) */
    if (HAL_I2C_IsDeviceReady(hi2c, HMC5883L_ADDR, 2, I2C_TIMEOUT) == HAL_OK)
    {
        /* Config Reg A (0x00): 8-sample average, 15Hz output rate, normal measurement */
        data = 0x70;
        HAL_I2C_Mem_Write(hi2c, HMC5883L_ADDR, 0x00, I2C_MEMADD_SIZE_8BIT, &data, 1, I2C_TIMEOUT);

        /* Config Reg B (0x01): Gain = 5 (±1.3 Gauss, 1090 LSB/Gauss) */
        data = 0x20;
        HAL_I2C_Mem_Write(hi2c, HMC5883L_ADDR, 0x01, I2C_MEMADD_SIZE_8BIT, &data, 1, I2C_TIMEOUT);

        /* Mode Reg (0x02): Continuous-measurement mode */
        data = 0x00;
        HAL_I2C_Mem_Write(hi2c, HMC5883L_ADDR, 0x02, I2C_MEMADD_SIZE_8BIT, &data, 1, I2C_TIMEOUT);

        mag->SensorType = MAG_TYPE_HMC5883L;
        HAL_Delay(10);
        return MAG_OK;
    }

    /* Try QMC5883L fallback (0x0D) */
    if (HAL_I2C_IsDeviceReady(hi2c, QMC5883L_ADDR, 2, I2C_TIMEOUT) == HAL_OK)
    {
        /* Set/Reset Period Reg (0x0B): 0x01 */
        data = 0x01;
        HAL_I2C_Mem_Write(hi2c, QMC5883L_ADDR, 0x0B, I2C_MEMADD_SIZE_8BIT, &data, 1, I2C_TIMEOUT);

        /* Control Reg 1 (0x09): Continuous Mode, 200Hz, 8G Range, 512 OSR (0x1D) */
        data = 0x1D;
        HAL_I2C_Mem_Write(hi2c, QMC5883L_ADDR, 0x09, I2C_MEMADD_SIZE_8BIT, &data, 1, I2C_TIMEOUT);

        mag->SensorType = MAG_TYPE_QMC5883L;
        HAL_Delay(10);
        return MAG_OK;
    }

    mag->SensorType = MAG_TYPE_NONE;
    return MAG_ERROR;
}

/**
 * @brief Read Raw Magnetic Field and convert to Gauss / uT
 */
Mag_Status HMC5883L_Read_Mag(I2C_HandleTypeDef *hi2c, HMC5883L_t *mag)
{
    uint8_t buf[6];
    int16_t raw_x, raw_y, raw_z;

    if (mag->SensorType == MAG_TYPE_HMC5883L)
    {
        /* HMC5883L: Reg 0x03..0x08 (Order: X_MSB, X_LSB, Z_MSB, Z_LSB, Y_MSB, Y_LSB) */
        if (HAL_I2C_Mem_Read(hi2c, HMC5883L_ADDR, 0x03, I2C_MEMADD_SIZE_8BIT, buf, 6, I2C_TIMEOUT) != HAL_OK)
        {
            return MAG_ERROR;
        }

        raw_x = (int16_t)(buf[0] << 8 | buf[1]);
        raw_z = (int16_t)(buf[2] << 8 | buf[3]);
        raw_y = (int16_t)(buf[4] << 8 | buf[5]);

        /* Gain = 1090 LSB/Gauss for ±1.3 Gauss */
        mag->Mag_X = (float)raw_x / 1090.0f;
        mag->Mag_Y = (float)raw_y / 1090.0f;
        mag->Mag_Z = (float)raw_z / 1090.0f;
    }
    else if (mag->SensorType == MAG_TYPE_QMC5883L)
    {
        /* QMC5883L: Reg 0x00..0x05 (Order: X_LSB, X_MSB, Y_LSB, Y_MSB, Z_LSB, Z_MSB) */
        if (HAL_I2C_Mem_Read(hi2c, QMC5883L_ADDR, 0x00, I2C_MEMADD_SIZE_8BIT, buf, 6, I2C_TIMEOUT) != HAL_OK)
        {
            return MAG_ERROR;
        }

        raw_x = (int16_t)(buf[1] << 8 | buf[0]);
        raw_y = (int16_t)(buf[3] << 8 | buf[2]);
        raw_z = (int16_t)(buf[5] << 8 | buf[4]);

        /* Sensitivity = 3000 LSB/Gauss for ±8 Gauss range */
        mag->Mag_X = (float)raw_x / 3000.0f;
        mag->Mag_Y = (float)raw_y / 3000.0f;
        mag->Mag_Z = (float)raw_z / 3000.0f;
    }
    else
    {
        return MAG_ERROR;
    }

    /* Compute Heading (0 to 360 degrees) */
    HMC5883L_GetHeading(mag, 0.0f);

    return MAG_OK;
}

/**
 * @brief Calculate Compass Heading with optional declination angle
 */
float HMC5883L_GetHeading(HMC5883L_t *mag, float declination_deg)
{
    float heading = atan2f(mag->Mag_Y, mag->Mag_X) * (180.0f / 3.14159265f);
    heading += declination_deg;

    if (heading < 0) heading += 360.0f;
    if (heading >= 360.0f) heading -= 360.0f;

    mag->Heading = heading;
    return heading;
}
