/**
  ******************************************************************************
  * @file           : bmp180.c
  * @brief          : BMP180 Barometer & Altimeter Driver Implementation
  * @author         : Sapuangin Telemetry Team
  * @description    : Reads factory EEPROM calibration coefficients, calculates
  *                   compensated temperature (C), pressure (hPa), and altitude (m).
  ******************************************************************************
  */

#include "bmp180.h"
#include <string.h>

#define I2C_TIMEOUT 100

/**
 * @brief Initialize BMP180 and read factory calibration coefficients
 */
BMP180_Status BMP180_Init(I2C_HandleTypeDef *hi2c, BMP180_t *bmp)
{
    uint8_t buf[22];
    memset(bmp, 0, sizeof(BMP180_t));

    /* Check if BMP180 is connected */
    if (HAL_I2C_IsDeviceReady(hi2c, BMP180_ADDR, 2, I2C_TIMEOUT) != HAL_OK)
    {
        return BMP180_ERROR;
    }

    /* Read 22 bytes of EEPROM calibration coefficients starting at 0xAA */
    if (HAL_I2C_Mem_Read(hi2c, BMP180_ADDR, 0xAA, I2C_MEMADD_SIZE_8BIT, buf, 22, I2C_TIMEOUT) != HAL_OK)
    {
        return BMP180_ERROR;
    }

    bmp->Calib.AC1 = (int16_t)(buf[0]  << 8 | buf[1]);
    bmp->Calib.AC2 = (int16_t)(buf[2]  << 8 | buf[3]);
    bmp->Calib.AC3 = (int16_t)(buf[4]  << 8 | buf[5]);
    bmp->Calib.AC4 = (uint16_t)(buf[6] << 8 | buf[7]);
    bmp->Calib.AC5 = (uint16_t)(buf[8] << 8 | buf[9]);
    bmp->Calib.AC6 = (uint16_t)(buf[10] << 8 | buf[11]);
    bmp->Calib.B1  = (int16_t)(buf[12] << 8 | buf[13]);
    bmp->Calib.B2  = (int16_t)(buf[14] << 8 | buf[15]);
    bmp->Calib.MB  = (int16_t)(buf[16] << 8 | buf[17]);
    bmp->Calib.MC  = (int16_t)(buf[18] << 8 | buf[19]);
    bmp->Calib.MD  = (int16_t)(buf[20] << 8 | buf[21]);

    return BMP180_OK;
}

/**
 * @brief Read uncompensated Temperature (UT)
 */
static int32_t BMP180_Get_UT(I2C_HandleTypeDef *hi2c)
{
    uint8_t cmd = 0x2E;
    uint8_t buf[2];

    /* Write 0x2E to 0xF4 to request Temperature measurement */
    HAL_I2C_Mem_Write(hi2c, BMP180_ADDR, 0xF4, I2C_MEMADD_SIZE_8BIT, &cmd, 1, I2C_TIMEOUT);
    HAL_Delay(5);  /* Wait 4.5ms */

    /* Read result from 0xF6 */
    if (HAL_I2C_Mem_Read(hi2c, BMP180_ADDR, 0xF6, I2C_MEMADD_SIZE_8BIT, buf, 2, I2C_TIMEOUT) != HAL_OK)
    {
        return 0;
    }
    return (int32_t)(buf[0] << 8 | buf[1]);
}

/**
 * @brief Read uncompensated Pressure (UP) with oss=0 (standard mode)
 */
static int32_t BMP180_Get_UP(I2C_HandleTypeDef *hi2c, uint8_t oss)
{
    uint8_t cmd = 0x34 + (oss << 6);
    uint8_t buf[3];

    /* Request Pressure measurement */
    HAL_I2C_Mem_Write(hi2c, BMP180_ADDR, 0xF4, I2C_MEMADD_SIZE_8BIT, &cmd, 1, I2C_TIMEOUT);
    HAL_Delay(5 + (3 << oss));  /* Delay based on OSS mode */

    /* Read result from 0xF6, 0xF7, 0xF8 */
    if (HAL_I2C_Mem_Read(hi2c, BMP180_ADDR, 0xF6, I2C_MEMADD_SIZE_8BIT, buf, 3, I2C_TIMEOUT) != HAL_OK)
    {
        return 0;
    }
    return (int32_t)(((buf[0] << 16) | (buf[1] << 8) | buf[2]) >> (8 - oss));
}

/**
 * @brief Read Temperature, Pressure, and Altitude
 */
BMP180_Status BMP180_Read_All(I2C_HandleTypeDef *hi2c, BMP180_t *bmp)
{
    int32_t UT = BMP180_Get_UT(hi2c);
    uint8_t oss = 0;  /* Standard mode */
    int32_t UP = BMP180_Get_UP(hi2c, oss);

    if (UT == 0 || UP == 0) return BMP180_ERROR;

    /* Calculate True Temperature */
    int32_t X1 = ((UT - (int32_t)bmp->Calib.AC6) * (int32_t)bmp->Calib.AC5) >> 15;
    int32_t X2 = ((int32_t)bmp->Calib.MC << 11) / (X1 + bmp->Calib.MD);
    int32_t B5 = X1 + X2;
    float temp = ((B5 + 8) >> 4) / 10.0f;
    bmp->Temperature_C = temp;

    /* Calculate True Pressure */
    int32_t B6 = B5 - 4000;
    X1 = (bmp->Calib.B2 * ((B6 * B6) >> 12)) >> 11;
    X2 = (bmp->Calib.AC2 * B6) >> 11;
    int32_t X3 = X1 + X2;
    int32_t B3 = (((((int32_t)bmp->Calib.AC1) * 4 + X3) << oss) + 2) >> 2;

    X1 = (bmp->Calib.AC3 * B6) >> 13;
    X2 = (bmp->Calib.B1 * ((B6 * B6) >> 12)) >> 16;
    X3 = ((X1 + X2) + 2) >> 2;
    uint32_t B4 = (bmp->Calib.AC4 * (uint32_t)(X3 + 32768)) >> 15;
    uint32_t B7 = ((uint32_t)UP - B3) * (50000 >> oss);

    int32_t p;
    if (B7 < 0x80000000)
    {
        p = (B7 << 1) / B4;
    }
    else
    {
        p = (B7 / B4) << 1;
    }

    X1 = (p >> 8) * (p >> 8);
    X1 = (X1 * 3038) >> 16;
    X2 = (-7357 * p) >> 16;
    p += (X1 + X2 + 3791) >> 4;

    float press_hPa = (float)p / 100.0f;
    bmp->Pressure_hPa = press_hPa;

    /* Calculate Altitude from Pressure (using standard barometric formula) */
    bmp->Altitude_m = 44330.0f * (1.0f - powf(press_hPa / 1013.25f, 0.19029495f));

    return BMP180_OK;
}
