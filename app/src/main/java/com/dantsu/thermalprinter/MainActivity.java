package com.dantsu.thermalprinter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.connection.tcp.TcpConnection;
import com.dantsu.escposprinter.connection.usb.UsbConnection;
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.dantsu.thermalprinter.async.AsyncBluetoothEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;
import com.dantsu.thermalprinter.async.AsyncTcpEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncUsbEscPosPrint;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private boolean isUsbReceiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnBrowse = findViewById(R.id.button_bluetooth_browse);
        if (btnBrowse != null) btnBrowse.setOnClickListener(view -> browseBluetoothDevice());

        Button btnBt = findViewById(R.id.button_bluetooth);
        if (btnBt != null) btnBt.setOnClickListener(view -> printBluetooth());

        Button btnUsb = findViewById(R.id.button_usb);
        if (btnUsb != null) btnUsb.setOnClickListener(view -> printUsb());

        Button btnTcp = findViewById(R.id.button_tcp);
        if (btnTcp != null) btnTcp.setOnClickListener(view -> printTcp());
    }

    /*==============================================================================================
    ====================================== BLUETOOTH PART ==========================================
    ==============================================================================================*/

    public interface OnBluetoothPermissionsGranted {
        void onPermissionsGranted();
    }

    public static final int PERMISSION_BLUETOOTH = 1;
    public static final int PERMISSION_BLUETOOTH_ADMIN = 2;
    public static final int PERMISSION_BLUETOOTH_CONNECT = 3;
    public static final int PERMISSION_BLUETOOTH_SCAN = 4;

    private OnBluetoothPermissionsGranted onBluetoothPermissionsGranted;

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0) return;

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkBluetoothPermissions(onBluetoothPermissionsGranted);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth Permission")
                    .setMessage("Izin Bluetooth diperlukan untuk menghubungkan ke printer thermal.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    public void checkBluetoothPermissions(OnBluetoothPermissionsGranted callback) {
        this.onBluetoothPermissionsGranted = callback;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, PERMISSION_BLUETOOTH);
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, PERMISSION_BLUETOOTH_ADMIN);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_BLUETOOTH_CONNECT);
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_BLUETOOTH_SCAN);
                return;
            }
        }

        if (callback != null) {
            callback.onPermissionsGranted();
        }
    }

    private BluetoothConnection selectedDevice;

    @SuppressLint("MissingPermission")
    private String getBluetoothDeviceName(BluetoothConnection device) {
        if (device == null || device.getDevice() == null) return "Printer Bluetooth";

        try {
            String name = device.getDevice().getName();
            return (name == null || name.trim().isEmpty()) ? "Printer Bluetooth" : name;
        } catch (SecurityException e) {
            return "Printer Bluetooth";
        }
    }

    public void browseBluetoothDevice() {
        checkBluetoothPermissions(() -> {
            final BluetoothConnection[] bluetoothDevicesList = new BluetoothPrintersConnections().getList();

            if (bluetoothDevicesList == null || bluetoothDevicesList.length == 0) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Bluetooth Printer")
                        .setMessage("Tidak ada printer Bluetooth yang ditemukan.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            final String[] items = new String[bluetoothDevicesList.length + 1];
            items[0] = "Printer Default";

            int i = 0;
            for (BluetoothConnection device : bluetoothDevicesList) {
                items[++i] = getBluetoothDeviceName(device);
            }

            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
            alertDialog.setTitle("Pilih Printer Bluetooth");
            alertDialog.setItems(items, (dialogInterface, selectedIndex) -> {
                int index = selectedIndex - 1;
                if (index == -1) {
                    selectedDevice = null;
                } else if (index >= 0 && index < bluetoothDevicesList.length) {
                    selectedDevice = bluetoothDevicesList[index];
                }

                Button button = findViewById(R.id.button_bluetooth_browse);
                if (button != null) button.setText(items[selectedIndex]);
            });

            AlertDialog alert = alertDialog.create();
            alert.setCanceledOnTouchOutside(false);
            alert.show();
        });
    }

    public void printBluetooth() {
        checkBluetoothPermissions(() -> {
            new AsyncBluetoothEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "Error Bluetooth: " + codeException);
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "Selesai Cetak Bluetooth.");
                        }
                    }
            ).execute(this.getAsyncEscPosPrinter(selectedDevice));
        });
    }

    /*==============================================================================================
    =========================================== USB PART ===========================================
    ==============================================================================================*/

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!ACTION_USB_PERMISSION.equals(action)) return;

            synchronized (this) {
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

                if (permissionGranted && usbManager != null && usbDevice != null) {
                    new AsyncUsbEscPosPrint(
                            context,
                            new AsyncEscPosPrint.OnPrintFinished() {
                                @Override
                                public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                                    Log.e("Async.OnPrintFinished", "Error USB: " + codeException);
                                }

                                @Override
                                public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                                    Log.i("Async.OnPrintFinished", "Selesai Cetak USB.");
                                }
                            }
                    ).execute(getAsyncEscPosPrinter(new UsbConnection(usbManager, usbDevice)));
                }
            }
        }
    };

    public void printUsb() {
        UsbConnection usbConnection = UsbPrintersConnections.selectFirstConnected(this);
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        if (usbConnection == null || usbManager == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Koneksi USB")
                    .setMessage("Printer USB tidak ditemukan.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION), pendingIntentFlags
        );

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }
        isUsbReceiverRegistered = true;

        usbManager.requestPermission(usbConnection.getDevice(), permissionIntent);
    }

    /*==============================================================================================
    =========================================== TCP PART ===========================================
    ==============================================================================================*/

    public void printTcp() {
        final EditText ipAddress = findViewById(R.id.edittext_tcp_ip);
        final EditText portAddress = findViewById(R.id.edittext_tcp_port);

        if (ipAddress == null || portAddress == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Error UI")
                    .setMessage("Input IP / Port tidak ditemukan di layout activity_main.xml.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String ip = ipAddress.getText().toString().trim();
        String portText = portAddress.getText().toString().trim();

        if (ip.isEmpty() || portText.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Koneksi TCP")
                    .setMessage("Isi IP dan Port Printer terlebih dahulu.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        try {
            int port = Integer.parseInt(portText);
            new AsyncTcpEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "Error TCP: " + codeException);
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "Selesai Cetak Network/TCP.");
                        }
                    }
            ).execute(this.getAsyncEscPosPrinter(new TcpConnection(ip, port)));
        } catch (NumberFormatException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Port Salah")
                    .setMessage("Port harus berupa angka (contoh: 9100).")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    /*==============================================================================================
    =================================== ESC/POS PRINTER PART ======================================
    ==============================================================================================*/

    @SuppressLint("SimpleDateFormat")
    public AsyncEscPosPrinter getAsyncEscPosPrinter(DeviceConnection printerConnection) {
        SimpleDateFormat format = new SimpleDateFormat("'tgl' yyyy-MM-dd 'jam' HH:mm:ss");

        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);

        return printer.addTextToPrint(
                "[C]<img>" +
                        PrinterTextParserImg.bitmapToHexadecimalString(
                                printer,
                                this.getApplicationContext().getResources().getDrawableForDensity(
                                        R.drawable.logo,
                                        DisplayMetrics.DENSITY_MEDIUM
                                )
                        ) +
                        "</img>\n" +
                        "[L]\n" +
                        "[C]<u><font size='big'>RESI PEMBAYARAN</font></u>\n" +
                        "[L]\n" +
                        "[C]<u type='double'>" + format.format(new Date()) + "</u>\n" +
                        "[C]================================\n" +
                        "[L]\n" +
                        "[L]<b>PRODUK CONTOH</b>[R]Rp 25.000\n" +
                        "[L]\n" +
                        "[C]--------------------------------\n" +
                        "[R]TOTAL :[R]Rp 25.000\n" +
                        "[L]\n" +
                        "[C]================================\n" +
                        "[L]\n" +
                        "[C]<qrcode size='20'>https://dantsu.com/</qrcode>\n"
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isUsbReceiverRegistered) {
            try {
                unregisterReceiver(usbReceiver);
                isUsbReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
    }
}
