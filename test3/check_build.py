"""Compile/link firmware with the CubeIDE ARM toolchain: python check_build.py <gcc.exe>."""
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
gcc = Path(sys.argv[1])
out = root / 'BuildCheck'
out.mkdir(exist_ok=True)
flags = ['-mcpu=cortex-m4', '-mthumb', '-mfpu=fpv4-sp-d16', '-mfloat-abi=hard',
         '-std=gnu11', '-O0', '-g3', '-ffunction-sections', '-fdata-sections',
         '-Wall', '-Wextra', '-Werror', '-DUSE_HAL_DRIVER', '-DSTM32F401xC']
includes = ['Core/Inc', 'Drivers/STM32F4xx_HAL_Driver/Inc',
            'Drivers/STM32F4xx_HAL_Driver/Inc/Legacy',
            'Drivers/CMSIS/Device/ST/STM32F4xx/Include', 'Drivers/CMSIS/Include']
flags += ['-I' + str(root / d) for d in includes]
sources = sorted((root / 'Core/Src').rglob('*.c')) + sorted((root / 'Drivers/STM32F4xx_HAL_Driver/Src').glob('*.c'))
exclusions = set()
for entry in ET.parse(root / '.cproject').iter('entry'):
    if entry.get('name') == 'Core':
        exclusions.update(entry.get('excluding', '').split('|'))
sources = [src for src in sources if src.relative_to(root).as_posix().removeprefix('Core/') not in exclusions]
sources += list((root / 'Core/Startup').glob('*.s'))
objects = []
for src in sources:
    obj = out / (src.stem + '.o')
    vendor_flags = ['-Wno-unused-parameter'] if 'Drivers' in src.parts else []
    subprocess.run([str(gcc), *flags, *vendor_flags, '-c', str(src), '-o', str(obj)], check=True)
    objects.append(str(obj))
elf = out / 'test3.elf'
subprocess.run([str(gcc), *flags, *objects, '-T' + str(root / 'STM32F401CCUX_FLASH.ld'),
                '--specs=nano.specs', '--specs=nosys.specs', '-Wl,--gc-sections',
                '-Wl,-Map=' + str(out / 'test3.map'), '-Wl,--print-memory-usage',
                '-Wl,--no-warn-rwx-segments', '-Wl,--fatal-warnings', '-o', str(elf)], check=True)
subprocess.run([str(gcc.with_name('arm-none-eabi-size.exe')), str(elf)], check=True)
subprocess.run([str(gcc.with_name('arm-none-eabi-objcopy.exe')), '-O', 'ihex', str(elf), str(out / 'test3.hex')], check=True)
print('PASS: compiled and linked', len(sources), 'sources; hardware checks still required.')
