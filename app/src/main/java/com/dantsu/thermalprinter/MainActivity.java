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

    /*==============================================================================================
    ========================================== ACTIVITY =============================================
    ==============================================================================================*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = findViewById(R.id.button_bluetooth_browse);
        button.setOnClickListener(view -> browseBluetoothDevice());

        button = findViewById(R.id.button_bluetooth);
        button.setOnClickListener(view -> printBluetooth());

        button = findViewById(R.id.button_usb);
        button.setOnClickListener(view -> printUsb());

        button = findViewById(R.id.button_tcp);
        button.setOnClickListener(view -> printTcp());
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

        if (grantResults.length == 0) {
            return;
        }

        switch (requestCode) {
            case PERMISSION_BLUETOOTH:
            case PERMISSION_BLUETOOTH_ADMIN:
            case PERMISSION_BLUETOOTH_CONNECT:
            case PERMISSION_BLUETOOTH_SCAN:

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkBluetoothPermissions(onBluetoothPermissionsGranted);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Bluetooth permission")
                            .setMessage(
                                    "Bluetooth permission is required to connect to a thermal printer."
                            )
                            .setPositiveButton("OK", null)
                            .show();
                }
                break;

            default:
                break;
        }
    }

    /**
     * Check Bluetooth permissions.
     *
     * Android < 12:
     * - BLUETOOTH
     * - BLUETOOTH_ADMIN
     *
     * Android 12+:
     * - BLUETOOTH_CONNECT
     * - BLUETOOTH_SCAN
     */
    public void checkBluetoothPermissions(
            OnBluetoothPermissionsGranted callback
    ) {
        this.onBluetoothPermissionsGranted = callback;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.BLUETOOTH},
                        PERMISSION_BLUETOOTH
                );
                return;
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                        PERMISSION_BLUETOOTH_ADMIN
                );
                return;
            }

        } else {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        PERMISSION_BLUETOOTH_CONNECT
                );
                return;
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN},
                        PERMISSION_BLUETOOTH_SCAN
                );
                return;
            }
        }

        if (callback != null) {
            callback.onPermissionsGranted();
        }
    }

    private BluetoothConnection selectedDevice;

    /**
     * Get Bluetooth printer name safely.
     *
     * The getName() method requires BLUETOOTH_CONNECT on
     * Android 12+.
     */
    @SuppressLint("MissingPermission")
    private String getBluetoothDeviceName(BluetoothConnection device) {

        if (device == null || device.getDevice() == null) {
            return "Bluetooth printer";
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED) {
                    return "Bluetooth printer";
                }
            } else {

                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED) {
                    return "Bluetooth printer";
                }
            }

            String name = device.getDevice().getName();

            if (name == null || name.trim().isEmpty()) {
                return "Bluetooth printer";
            }

            return name;

        } catch (SecurityException e) {
            Log.w(
                    "Bluetooth",
                    "Unable to read Bluetooth printer name",
                    e
            );

            return "Bluetooth printer";
        }
    }

    public void browseBluetoothDevice() {

        checkBluetoothPermissions(() -> {

            final BluetoothConnection[] bluetoothDevicesList =
                    new BluetoothPrintersConnections().getList();

            if (bluetoothDevicesList == null ||
                    bluetoothDevicesList.length == 0) {

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Bluetooth printer")
                        .setMessage("No Bluetooth printer found.")
                        .setPositiveButton("OK", null)
                        .show();

                return;
            }

            final String[] items =
                    new String[bluetoothDevicesList.length + 1];

            items[0] = "Default printer";

            int i = 0;

            for (BluetoothConnection device : bluetoothDevicesList) {
                items[++i] = getBluetoothDeviceName(device);
            }

            AlertDialog.Builder alertDialog =
                    new AlertDialog.Builder(MainActivity.this);

            alertDialog.setTitle("Bluetooth printer selection");

            alertDialog.setItems(
                    items,
                    (dialogInterface, selectedIndex) -> {

                        int index = selectedIndex - 1;

                        if (index == -1) {
                            selectedDevice = null;
                        } else if (index >= 0 &&
                                index < bluetoothDevicesList.length) {

                            selectedDevice =
                                    bluetoothDevicesList[index];
                        }

                        Button button =
                                findViewById(
                                        R.id.button_bluetooth_browse
                                );

                        button.setText(items[selectedIndex]);
                    }
            );

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
                        public void onError(
                                AsyncEscPosPrinter asyncEscPosPrinter,
                                int codeException
                        ) {
                            Log.e(
                                    "Async.OnPrintFinished",
                                    "Bluetooth printing error: "
                                            + codeException
                            );
                        }

                        @Override
                        public void onSuccess(
                                AsyncEscPosPrinter asyncEscPosPrinter
                        ) {
                            Log.i(
                                    "Async.OnPrintFinished",
                                    "Bluetooth print finished."
                            );
                        }
                    }
            ).execute(
                    this.getAsyncEscPosPrinter(selectedDevice)
            );
        });
    }

    /*==============================================================================================
    =========================================== USB PART ===========================================
    ==============================================================================================*/

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver =
            new BroadcastReceiver() {

        @Override
        public void onReceive(
                Context context,
                Intent intent
        ) {

            String action = intent.getAction();

            if (!ACTION_USB_PERMISSION.equals(action)) {
                return;
            }

            synchronized (this) {

                UsbManager usbManager =
                        (UsbManager) getSystemService(
                                Context.USB_SERVICE
                        );

                UsbDevice usbDevice;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                    usbDevice = intent.getParcelableExtra(
                            UsbManager.EXTRA_DEVICE,
                            UsbDevice.class
                    );

                } else {

                    usbDevice = intent.getParcelableExtra(
                            UsbManager.EXTRA_DEVICE
                    );
                }

                boolean permissionGranted =
                        intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false
                        );

                if (permissionGranted &&
                        usbManager != null &&
                        usbDevice != null) {

                    new AsyncUsbEscPosPrint(
                            context,
                            new AsyncEscPosPrint.OnPrintFinished() {

                                @Override
                                public void onError(
                                        AsyncEscPosPrinter asyncEscPosPrinter,
                                        int codeException
                                ) {
                                    Log.e(
                                            "Async.OnPrintFinished",
                                            "USB printing error: "
                                                    + codeException
                                    );
                                }

                                @Override
                                public void onSuccess(
                                        AsyncEscPosPrinter asyncEscPosPrinter
                                ) {
                                    Log.i(
                                            "Async.OnPrintFinished",
                                            "USB print finished."
                                    );
                                }
                            }
                    ).execute(
                            getAsyncEscPosPrinter(
                                    new UsbConnection(
                                            usbManager,
                                            usbDevice
                                    )
                            )
                    );
                }
            }
        }
    };

    public void printUsb() {

        UsbConnection usbConnection =
                UsbPrintersConnections.selectFirstConnected(this);

        UsbManager usbManager =
                (UsbManager) getSystemService(
                        Context.USB_SERVICE
                );

        if (usbConnection == null || usbManager == null) {

            new AlertDialog.Builder(this)
                    .setTitle("USB Connection")
                    .setMessage("No USB printer found.")
                    .setPositiveButton("OK", null)
                    .show();

            return;
        }

        int pendingIntentFlags =
                PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent permissionIntent =
                PendingIntent.getBroadcast(
                        this,
                        0,
                        new Intent(ACTION_USB_PERMISSION),
                        pendingIntentFlags
                );

        IntentFilter filter =
                new IntentFilter(ACTION_USB_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            registerReceiver(
                    usbReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    usbReceiver,
                    filter
            );
        }

        usbManager.requestPermission(
                usbConnection.getDevice(),
                permissionIntent
        );
    }

    /*==============================================================================================
    =========================================== TCP PART ===========================================
    ==============================================================================================*/

    public void printTcp() {

        final EditText ipAddress =
                findViewById(R.id.edittext_tcp_ip);

        final EditText portAddress =
                findViewById(R.id.edittext_tcp_port);

        String ip = ipAddress.getText().toString().trim();
        String portText = portAddress.getText().toString().trim();

        if (ip.isEmpty()) {

            new AlertDialog.Builder(this)
                    .setTitle("TCP connection")
                    .setMessage("Please enter printer IP address.")
                    .setPositiveButton("OK", null)
                    .show();

            return;
        }

        if (portText.isEmpty()) {

            new AlertDialog.Builder(this)
                    .setTitle("TCP connection")
                    .setMessage("Please enter printer port.")
                    .setPositiveButton("OK", null)
                    .show();

            return;
        }

        try {

            int port = Integer.parseInt(portText);

            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }

            new AsyncTcpEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {

                        @Override
                        public void onError(
                                AsyncEscPosPrinter asyncEscPosPrinter,
                                int codeException
                        ) {
                            Log.e(
                                    "Async.OnPrintFinished",
                                    "TCP printing error: "
                                            + codeException
                            );
                        }

                        @Override
                        public void onSuccess(
                                AsyncEscPosPrinter asyncEscPosPrinter
                        ) {
                            Log.i(
                                    "Async.OnPrintFinished",
                                    "TCP print finished."
                            );
                        }
                    }
            ).execute(
                    this.getAsyncEscPosPrinter(
                            new TcpConnection(ip, port)
                    )
            );

        } catch (NumberFormatException e) {

            new AlertDialog.Builder(this)
                    .setTitle("Invalid TCP port address")
                    .setMessage(
                            "Port must be a number between 1 and 65535."
                    )
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    /*==============================================================================================
    =================================== ESC/POS PRINTER PART ======================================
    ==============================================================================================*/

    /**
     * Create asynchronous ESC/POS printer.
     */
    @SuppressLint("SimpleDateFormat")
    public AsyncEscPosPrinter getAsyncEscPosPrinter(
            DeviceConnection printerConnection
    ) {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "'on' yyyy-MM-dd 'at' HH:mm:ss"
                );

        AsyncEscPosPrinter printer =
                new AsyncEscPosPrinter(
                        printerConnection,
                        203,
                        48f,
                        32
                );

        return printer.addTextToPrint(

                "[C]<img>" +
                        PrinterTextParserImg.bitmapToHexadecimalString(
                                printer,
                                this.getApplicationContext()
                                        .getResources()
                                        .getDrawableForDensity(
                                                R.drawable.logo,
                                                DisplayMetrics.DENSITY_MEDIUM
                                        )
                        ) +
                        "</img>\n" +

                "[L]\n" +

                "[C]<u><font size='big'>" +
                        "ORDER N°045" +
                        "</font></u>\n" +

                "[L]\n" +

                "[C]<u type='double'>" +
                        format.format(new Date()) +
                        "</u>\n" +

                "[C]\n" +

                "[C]================================\n" +

                "[L]\n" +

                "[L]<b>BEAUTIFUL SHIRT</b>[R]9.99€\n" +

                "[L]  + Size : S\n" +

                "[L]\n" +

                "[L]<b>AWESOME HAT</b>[R]24.99€\n" +

                "[L]  + Size : 57/58\n" +

                "[L]\n" +

                "[C]--------------------------------\n" +

                "[R]TOTAL PRICE :[R]34.98€\n" +

                "[R]TAX :[R]4.23€\n" +

                "[L]\n" +

                "[C]================================\n" +

                "[L]\n" +

                "[L]<u>" +
                        "<font color='bg-black' size='tall'>" +
                        "Customer :" +
                        "</font></u>\n" +

                "[L]Raymond DUPONT\n" +

                "[L]5 rue des girafes\n" +

                "[L]31547 PERPETES\n" +

                "[L]Tel : +33801201456\n" +

                "\n" +

                "[C]<barcode type='ean13' height='10'>" +
                        "831254784551" +
                        "</barcode>\n" +

                "[L]\n" +

                "[C]<qrcode size='20'>" +
                        "https://dantsu.com/" +
                        "</qrcode>\n"
        );
    }

    /*==============================================================================================
    ======================================= ACTIVITY CLEANUP ======================================
    ==============================================================================================*/

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was not registered.
        }
    }
}
