# test3, Black Pill STM32F401CC

## Wiring
MAX SDA -> PB7, SCL -> PB6, INT -> PB5, GND -> GND.
VIN: use the breakout supply verified during the idle-voltage measurement.
SDA/SCL were measured at 3.3 V. No extra I2C pull-up required.
PA9 TX -> USB-UART RX, PA10 RX -> USB-UART TX (optional), shared GND.
Use 3.3 V UART logic. Black Pill USB is not configured as a serial port.

## CubeIDE
Refresh test3 with F5 and reopen test3.ioc from disk.
I2C1: PB6/PB7, 100 kHz, AF4 Open Drain, no internal pull-up.
USART1: PA9/PA10, 115200 baud, 8N1, no flow control.
PB5: falling-edge EXTI, internal pull-up; EXTI9_5 enabled.
PC13: active-low onboard LED. Clock stays on HSI 16 MHz.
Build, then flash through ST-LINK. Open the UART terminal at 115200 8N1.

## Output
START,test3
READY,MAX30102,100SPS
DATA,hr=...,spo2=...,hr_valid=...,spo2_valid=...,finger=...,red=...,ir=...

The first calculation needs about five seconds of stable finger contact.
-999 with valid=0 means unavailable, not a measurement.
ERROR,NO_ACK: check supply, ground, PB6/PB7, solder joints.
ERROR,PART_ID: expected register 0xFF = 0x15.
ERROR,FIFO_OR_I2C: sample gap or bus failure; firmware retries initialization.
Finger thresholds in MAX30102.h are inherited from upstream and need checking
against red/ir output on your physical module. Algorithm validity is not a
signal-quality guarantee. HR/SpO2 accuracy has not been calibrated.

## Sources and changes
https://github.com/lamik/MAX30102_STM32_HAL
https://msalamon.pl/palec-mi-pulsuje-pulsometr-max30102-pod-kontrola-stm32/
Upstream driver/algorithm licenses are preserved in the source files and
LICENSE-MAX30102. See UPSTREAM.txt for the pinned commit.
Adapted F401RE to F401CC. FIFO reads run in the main loop, not the ISR.
5 ms polling also works without INT. Added bounded bus/reset timeouts,
overflow recovery, startup ID check, validity flags, UART diagnostics.
Fixed register field masks and algorithm bounds, valley indexing and
intermediate multiplication overflow. No MQTT is configured yet.

## Repeatable compile/link check
python check_build.py "<CubeIDE ARM toolchain>/bin/arm-none-eabi-gcc.exe"
BuildCheck/test3.elf and .hex are generated with the project linker script.
Startup also checks register readback: SpO2 config must be 0x27
(100 samples/s, 4096 nA range, 411 us pulse), mode must be SpO2.
ERROR,CONFIG_READBACK means initialization did not persist as expected.
No physical sensor/serial measurements were performed during preparation.

Previous MPU6050/HMC5883L/BMP180 source files are excluded in .cproject.
Use Project > Clean before building; select test3 Debug for launch.
