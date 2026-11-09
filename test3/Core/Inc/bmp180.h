/**
  ******************************************************************************
  * @file           : bmp180.h
  * @brief          : Header for BMP180 Digital Barometric Pressure & Temp Driver
  * @author         : Sapuangin Telemetry Team
  ******************************************************************************
  */

#ifndef __BMP180_H
#define __BMP180_H

#ifdef __cplusplus
extern "C" {
#endif

#include "stm32f4xx_hal.h"
#include <math.h>

#define BMP180_ADDR (0x77 << 1)

typedef enum {
    BMP180_OK = 0,
    BMP180_ERROR
} BMP180_Status;

typedef struct {
    int16_t  AC1, AC2, AC3;
    uint16_t AC4, AC5, AC6;
    int16_t  B1, B2, MB, MC, MD;
} BMP180_Calib_t;

typedef struct {
    float Temperature_C;
    float Pressure_hPa;
    float Altitude_m;
    BMP180_Calib_t Calib;
} BMP180_t;

BMP180_Status BMP180_Init(I2C_HandleTypeDef *hi2c, BMP180_t *bmp);
BMP180_Status BMP180_Read_All(I2C_HandleTypeDef *hi2c, BMP180_t *bmp);

#ifdef __cplusplus
}
#endif

#endif /* __BMP180_H */
