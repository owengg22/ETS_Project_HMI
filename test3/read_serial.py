import sys
import time
import json

try:
    import serial
    import serial.tools.list_ports
except ImportError:
    print("Error: Library 'pyserial' belum terinstall.")
    print("Silakan install dengan menjalankan perintah: pip install pyserial")
    sys.exit(1)

def list_com_ports():
    ports = serial.tools.list_ports.comports()
    return [port.device for port in ports]

def main():
    print("=== MPU6050 STM32 Serial Monitor ===")
    
    # 1. Deteksi COM Ports
    ports = list_com_ports()
    if not ports:
        print("\n[PERINGATAN] Tidak ada COM port aktif yang terdeteksi.")
        print("Silakan hubungkan USB-to-UART Converter Anda ke laptop.")
        print("Tekan Enter untuk mencoba mendeteksi ulang...")
        input()
        ports = list_com_ports()
        if not ports:
            print("Tetap tidak terdeteksi. Silakan periksa koneksi kabel/driver USB-to-UART Anda.")
            sys.exit(1)
            
    print("\nCOM Port yang tersedia:")
    for i, port in enumerate(ports):
        print(f"[{i}] {port}")
        
    # Pilih COM Port
    if len(ports) == 1:
        port_index = 0
        selected_port = ports[port_index]
        print(f"\nOtomatis memilih port tunggal: {selected_port}")
    else:
        try:
            choice = input(f"\nPilih port [0-{len(ports)-1}]: ")
            port_index = int(choice)
            selected_port = ports[port_index]
        except (ValueError, IndexError):
            print("Pilihan tidak valid, menggunakan port pertama.")
            selected_port = ports[0]
            
    print(f"\nMenghubungkan ke {selected_port} dengan baud rate 115200...")
    
    try:
        with serial.Serial(selected_port, 115200, timeout=1) as ser:
            print(f"Berhasil terhubung ke {selected_port}!")
            print("Membaca data sensor (Tekan Ctrl+C untuk berhenti):\n")
            ser.reset_input_buffer()
            
            # Header untuk tabel visual jika datanya valid
            print(f"{'PITCH':^8} | {'ROLL':^8} | {'YAW':^8} | {'AX':^6} | {'AY':^6} | {'AZ':^6} | {'TEMP':^6}")
            print("-" * 65)
            
            while True:
                line = ser.readline().decode('utf-8', errors='ignore').strip()
                if not line:
                    continue
                
                # Coba parse JSON
                try:
                    data = json.loads(line)
                    pitch = data.get("pitch", 0.0)
                    roll = data.get("roll", 0.0)
                    yaw = data.get("yaw", 0.0)
                    ax = data.get("ax", 0.0)
                    ay = data.get("ay", 0.0)
                    az = data.get("az", 0.0)
                    temp = data.get("temp", 0.0)
                    
                    # Print dalam format tabel yang rapi
                    print(f"{pitch:>8.2f} | {roll:>8.2f} | {yaw:>8.2f} | {ax:>6.2f} | {ay:>6.2f} | {az:>6.2f} | {temp:>5.1f}°C", end="\r")
                except json.JSONDecodeError:
                    # Jika data boot/kalibrasi bukan JSON biasa, cetak raw text saja
                    print(f"\n[RAW]: {line}")
                    
    except serial.SerialException as e:
        print(f"\n[ERROR] Koneksi serial terputus atau port sedang digunakan: {e}")
    except KeyboardInterrupt:
        print("\n\nMonitoring dihentikan. Sampai jumpa!")

if __name__ == "__main__":
    main()
