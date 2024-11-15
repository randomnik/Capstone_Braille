package com.example.brailleapp;

import android.Manifest;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.Toast;
import androidx.annotation.NonNull;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.io.File;
import android.os.ParcelUuid;

//CameraX
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import android.view.View;

//MLKit
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import androidx.camera.core.ExperimentalGetImage;

//BLE
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import java.util.UUID;

//TTS
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    //Camera
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private View rectOverlay;
    private Camera camera;

    //TTS
    private TextToSpeech tts;

    //BLE
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic characteristic;
    private static final String SERVICE_UUID = "8e0fc27d-a3a0-4aa7-850d-c0cb8bccee6b"; //service UUID
    private static final String CHARACTERISTIC_UUID = "6d796c1a-ebcb-450d-a96c-218f904da505"; //characteristic UUID
    private BluetoothDevice connectedDevice;
    private final Handler adRestartHandler = new Handler(Looper.getMainLooper());
    private static final int AD_RESTART_DELAY_MS = 5000; //advertising 재시도
    private boolean isAdvertising = false;

    //초기화 작업
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //activity_main 레이아웃 표시
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //카메라
        previewView = findViewById(R.id.previewView);
        rectOverlay = findViewById(R.id.rectOverlay);
        requestCameraPermission();

        //촬영 버튼
        Button photoButton = findViewById(R.id.Photo);
        photoButton.setOnClickListener(view -> takePhoto());

        //BLE
        //Android12 이상일 시 BLE 관련 권한 요청
        //권한 이미 부여 시 BLE 초기화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissions();
        } else {
            initializeBluetooth();
        }

        // TTS 초기화
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.KOREAN); //한국어 설정
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(MainActivity.this, "TTS: 한국어 지원 불가", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "TTS 초기화 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }



    //BLE 초기화
    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "블루투스가 비활성 상태입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        //GATT 서버 실행
        try {
            gattServer = bluetoothManager.openGattServer(this, gattServerCallback);
            setupGattServer();  //GATT 서버 설정

            //BLE advertising 시작 전 권한 확인
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android12 이상 >> BLUETOOTH_ADVERTISE 권한 확인
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "블루투스 Advertise 권한 필요", Toast.LENGTH_SHORT).show();
                    return;  //권한 없다면 advertising 시작X
                }
            }

            //GATT 서버 설정 후 advertising 시작
            bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            if (bluetoothLeAdvertiser != null) {
                startAdvertising();  //advertising 시작
            } else {
                Toast.makeText(this, "기기가 블루투스 Advertising을 지원하지 않습니다", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "블루투스 권한 오류", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }



    //Android 12 이상 BLE 관련 권한 요청
    //연결, 광고, 스캔 권한 설정
    private void requestBluetoothPermissions() {
        ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                    Boolean connectGranted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false);

                    if (connectGranted) {
                        initializeBluetooth();  // BLE 초기화
                    } else {
                        Toast.makeText(this, "Bluetooth Connect permission is required", Toast.LENGTH_SHORT).show();
                    }
                });

        //클래식 블루투스 권한(BLUETOOTH) 없이 BLE 관련 권한만 요청
        requestMultiplePermissionsLauncher.launch(new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN
        });
    }



    //BLE advertisement 설정&시작
    private void startAdvertising() {
        // 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth Advertise permission is required", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        //advertisement 설정(fast)
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)  //연결 유지 위한 광고 모드
                .setConnectable(true) //연결 가능하게 설정
                .setTimeout(0) //타임아웃X
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)  //전송 전력 중간
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true) //기기 이름 포함
                .addServiceUuid(ParcelUuid.fromString(SERVICE_UUID)) //서비스 UUID 추가
                .build();

        //BLE advertising 시작
        try {
            bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback); //advertisement 시작
        } catch (SecurityException e) {
            // 예외 처리
            Toast.makeText(this, "Bluetooth permission denied for advertising", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }




    //advertising 실패 시 재시도하기 위한 AdvertiseCallback 수정
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Toast.makeText(MainActivity.this, "Advertising Started", Toast.LENGTH_SHORT).show();
            isAdvertising = true; //advertising 시작 표시
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Toast.makeText(MainActivity.this, "Advertising Failed: " + errorCode, Toast.LENGTH_SHORT).show();
            isAdvertising = false; //advertising 실패 시 상태 false 설정

            //광고 재시도 로직 추가
            adRestartHandler.postDelayed(() -> {
                if (!isAdvertising) { //여전히 광고 중단된 상태일 때만 재시작
                    startAdvertising();
                }
            }, AD_RESTART_DELAY_MS); //10초 후 재시도
        }
    };



    //주기적 연결 상태 확인 및 재연결 로직 추가
    @Override
    public void onResume() {
        super.onResume();

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (connectedDevice == null && !isAdvertising) {
                    startAdvertising(); //광고가 중단된 경우 다시 시작
                }
                handler.postDelayed(this, 5000); //10초마다 반복 실행
            }
        }, 5000); //최초 지연 시간 설정
    }




    //GATT 서버 설정
    private void setupGattServer() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

        //블루투스 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth Connect permission is required", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        //GATT 서버 시작
        try {
            gattServer = bluetoothManager.openGattServer(this, gattServerCallback);

            BluetoothGattService service = new BluetoothGattService(
                    UUID.fromString(SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY);

            //GATT characteristic 생성
            characteristic = new BluetoothGattCharacteristic(
                    UUID.fromString(CHARACTERISTIC_UUID),
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ);

            BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                    BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            characteristic.addDescriptor(descriptor);

            //생성한 characteristic 서비스에 추가, 서비스 GATT 서버에 등록
            service.addCharacteristic(characteristic);
            gattServer.addService(service);
        } catch (SecurityException e) {
            Toast.makeText(this, "블루투스 권한 오류", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }




    //GATT 서버 callback
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        //연결 상태 변경 시
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device;
                Toast.makeText(MainActivity.this, "Device Connected", Toast.LENGTH_SHORT).show();

                //GATT 서비스 설정은 연결 후에 초기화
                setupGattServer();  //연결 후 GATT 서버 초기화

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null;
                Toast.makeText(MainActivity.this, "Device Disconnected", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid().toString())) {
                //권한 체크
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(MainActivity.this, "Bluetooth Connect permission is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                try {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
                } catch (SecurityException e) {
                    Toast.makeText(MainActivity.this, "Bluetooth Connect permission denied", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        }
    };



    //카메라 권한 요청
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }


    //권한 요청 처리
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "권한 필요", Toast.LENGTH_SHORT).show();
                }
            });


    //카메라 시작
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                //촬영 기능
                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                //OCR ImageAnalysis 설정. 최신 이미지만 처리
                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                //OCR 작동
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
                    @ExperimentalGetImage
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        processImageProxy(imageProxy);  // 이미지 분석 및 OCR 처리
                    }
                });

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    //사진 촬영
    private void takePhoto() {
        if (imageCapture != null) {
            File photoFile = new File(getOutputDirectory(), System.currentTimeMillis() + ".jpg");

            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

            imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Toast.makeText(MainActivity.this, "저장 성공: " + photoFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            exception.printStackTrace();
                            Toast.makeText(MainActivity.this, "저장 실패", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        }
    }

    //사진 저장 경로
    private File getOutputDirectory() {
        File mediaDir = getExternalMediaDirs()[0];
        File outputDir = new File(mediaDir, getResources().getString(R.string.app_name));
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        return outputDir;
    }

    //Rect
    private int convertDpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    //OCR 타이밍
    private boolean isCameraPaused = false;
    private static final long CAMERA_PAUSE_DURATION = 5000;

    //OCR
    @ExperimentalGetImage
    private void processImageProxy(ImageProxy imageProxy) {
        if (isCameraPaused) {
            imageProxy.close();
            return;
        }

        //imageProxy에서 이미지 가져오기
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;  //이미지가 없으면 바로 반환하여 강제 종료 방지
        }

        //텍스트 인식 위한 현재 이미지 정보
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

        //텍스트 인식 영역 설정
        int previewWidth = previewView.getWidth();
        int previewHeight = previewView.getHeight();

        //오버레이 영역 계산
        int[] overlayLocation = new int[2];
        rectOverlay.getLocationInWindow(overlayLocation);
        int overlayLeft = overlayLocation[0] - previewView.getLeft();
        int overlayTop = overlayLocation[1] - previewView.getTop();
        int overlayRight = overlayLeft + rectOverlay.getWidth();
        int overlayBottom = overlayTop + rectOverlay.getHeight();

        float scaleX = (float) imageWidth / previewWidth;
        float scaleY = (float) imageHeight / previewHeight;

        int offset = convertDpToPx(50);
        //오버레이 영역 객체 생성
        RectF overlayRect = new RectF(
                overlayLeft * scaleX,
                (overlayTop + offset) * scaleY, //상단 -50dp
                overlayRight * scaleX,
                overlayBottom * scaleY
        );

        //현재 카메라 프레임 OCR 사용, 텍스트 인식 초기화
        try {
            InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), rotationDegrees);
            com.google.mlkit.vision.text.TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

            recognizer.process(inputImage)
                    .addOnSuccessListener(visionText -> {
                        boolean textDetectedInOverlay = false; //오버레이 영역 텍스트 포함 여부 확인

                        for (Text.TextBlock block : visionText.getTextBlocks()) {
                            Rect boundingBox = block.getBoundingBox();
                            if (boundingBox != null) {
                                //boundingBox 좌표 >> RectF 변환
                                RectF adjustedBoundingBox = new RectF(
                                        boundingBox.left,
                                        boundingBox.top,
                                        boundingBox.right,
                                        boundingBox.bottom
                                );

                                //오버레이와 영역 겹치는지 확인
                                if (RectF.intersects(adjustedBoundingBox, overlayRect)) {
                                    textDetectedInOverlay = true;
                                    String recognizedText = block.getText();
                                    //BLE 텍스트 전송
                                    sendTextOverBLE(recognizedText);

                                    //인식된 텍스트 TTS로 읽음
                                    speakOut(recognizedText);

                                    Toast.makeText(MainActivity.this, recognizedText, Toast.LENGTH_SHORT).show();
                                    break;
                                }
                            }
                        }

                        if (textDetectedInOverlay) {
                            pauseCamera();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this, "텍스트 인식 실패", Toast.LENGTH_SHORT).show();
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });
        } catch (Exception e) {
            //예외 처리
            Toast.makeText(this, "OCR 처리 중 예외 발생: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            imageProxy.close(); // 예외 발생 시에도 반드시 close
        }
    }

    //TTS 출력
    private void speakOut(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    //Activitiy 종료 시 호출
    @Override
    protected void onDestroy() {
        // TTS 종료
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    //인식된 텍스트 BLE 사용해 전송
    private void sendTextOverBLE(String text) {
        if (characteristic != null && connectedDevice != null) {  // connectedDevice가 null이 아닌지 확인
            try {
                //권한 체크
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "블루투스 연결 권한 필요", Toast.LENGTH_SHORT).show();
                        return; //권한X >> 데이터 전송X
                    }
                }
                characteristic.setValue(text.getBytes()); //인식된 텍스트 >> characteristic 값 설정
                gattServer.notifyCharacteristicChanged(connectedDevice, characteristic, false); // 연결된 장치에 알림 전송
            } catch (Exception e) {
                // 예외 처리
                Toast.makeText(this, "BLE 전송 중 오류 발생: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            // 연결된 장치가 없으면 BLE 데이터를 전송할 수 없음을 알림
            Toast.makeText(this, "연결된 장치 없음", Toast.LENGTH_SHORT).show();
        }
    }

    //OCR 타이밍
    private void pauseCamera() {
        isCameraPaused = true;
        new android.os.Handler().postDelayed(() -> {
            isCameraPaused = false;
        }, CAMERA_PAUSE_DURATION);
    }
}