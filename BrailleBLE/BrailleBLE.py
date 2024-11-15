#비동기, BLE, Serial, 전송 대기
import asyncio
from bleak import BleakClient, BleakScanner
import serial
from time import sleep

#BLE 장치 정보 설정: 장치 이름, 사용자정의 UUID
DEVICE_NAME = "Device Name"
SERVICE_UUID = "8e0fc27d-a3a0-4aa7-850d-c0cb8bccee6b"
CHARACTERISTIC_UUID = "6d796c1a-ebcb-450d-a96c-218f904da505"

#시리얼 설정, 9600bps, 텍스트 수신 플래그
ser = serial.Serial('/dev/ttyUSB0', 9600)
text = ""
new_text_received = False


#BLE 장치 UUID 기반 검색 비동기 함수
async def scan_and_connect():
    print("장치 검색 및 연결 시도 중...")

    #장치 검색 반복 실행
    while True:
        devices = await BleakScanner.discover()
        device = None

        #SERVICE_UUID 포함된 장치 탐색
        for d in devices:
            for service in d.metadata["uuids"]:
                if SERVICE_UUID in service:
                    device = d
                    break

        if device:
            #장치 발견 시 연결 시도
            client = BleakClient(device)
            connected = await try_connect_and_notify(client)
            if connected:
                #연결 성공 시 검색 루프 중지
                return client
            else:
                print("BLE 장치와 연결에 실패했습니다. 0.5초 후 재검색합니다.")
        else:
            print("장치를 찾을 수 없습니다. 0.5초 후 재검색합니다.")
        await asyncio.sleep(0.5)


#BLE 장치 연결 및 알림 설정 비동기 함수
async def try_connect_and_notify(client):
    try:
        #장치 연결 시
        await client.connect()
        if client.is_connected:
            print(f"장치에 연결되었습니다. 장치 주소: {client.address}")

            services = await client.get_services()
            characteristic = services.get_characteristic(CHARACTERISTIC_UUID)
            
            #특성에서 notify 발견 시 알림 시작 
            if 'notify' in characteristic.properties:
                await client.start_notify(CHARACTERISTIC_UUID, notification_handler)
                print("알림 시작")
            return True
        else:
            print("BLE 장치와 연결되지 않았습니다.")
            return False
    #예외 처리
    except Exception as e:
        print(f"BLE 연결 오류: {e}")
        return False


#BLE 데이터 수신 함수
def notification_handler(sender: int, data: bytearray):
    global new_text_received, text
    text = data.decode("utf-8")
    #텍스트 수신 플래그 사용해 수신 표시
    new_text_received = True
    print(f"수신한 텍스트: {text}")

    #시리얼 전송
    for char in text:
        ser.write(char.encode())
        print(f"아두이노로 '{char}' 전송.")
        sleep(0.5)


#BLE 연결 상태 확인 및 재연결 함수
async def monitor_connection(client):
    while True:
        if not client.is_connected:
            print("연결이 끊어졌습니다. 재연결을 시도합니다.")
            await scan_and_connect()
        await asyncio.sleep(5)


#main
async def main():
    client = await scan_and_connect()
    if client:
        asyncio.create_task(monitor_connection(client))


#실행
asyncio.run(main())