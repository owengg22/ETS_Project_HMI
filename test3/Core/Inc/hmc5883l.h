/**
  ******************************************************************************
  * @file           : hmc5883l.h
  * @brief          : Header for HMC5883L / QMC5883L 3-Axis Magnetometer Driver
  * @author         : Sapuangin Telemetry Team
  ******************************************************************************
  */

#ifndef __HMC5883L_H
#define __HMC5883L_H

#ifdef __cplusplus
extern "C" {
#endif

#include "stm32f4xx_hal.h"
#include <math.h>

/* I2C Addresses (7-bit shifted left for HAL) */
#define HMC5883L_ADDR             (0x1E << 1)
#define QMC5883L_ADDR             (0x0D << 1)

/* Sensor Type Identifier */
typedef enum {
    MAG_TYPE_NONE = 0,
    MAG_TYPE_HMC5883L,
    MAG_TYPE_QMC5883L
} Mag_Type_t;

/* Status Enumeration */
typedef enum {
    MAG_OK = 0,
    MAG_ERROR
} Mag_Status;

/* Data Structure */
typedef struct {
    float Mag_X;          /* Gauss or uT */
    float Mag_Y;
    float Mag_Z;
    float Heading;        /* Degrees (0 - 360) */
    Mag_Type_t SensorType;
} HMC5883L_t;

/* Public API Functions */
Mag_Status HMC5883L_Init(I2C_HandleTypeDef *hi2c, HMC5883L_t *mag);
Mag_Status HMC5883L_Read_Mag(I2C_HandleTypeDef *hi2c, HMC5883L_t *mag);
float HMC5883L_GetHeading(HMC5883L_t *mag, float declination_deg);

#ifdef __cplusplus
}
#endif

#endif /* __HMC5883L_H */
