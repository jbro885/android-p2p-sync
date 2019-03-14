package org.smartregister.p2p.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.util.SparseArray;
import android.view.LayoutInflater;

import com.google.android.gms.vision.barcode.Barcode;

import org.smartregister.p2p.R;
import org.smartregister.p2p.view.QRCodeScannerView;

/**
 * Created by Ephraim Kigamba - ekigamba@ona.io on 08/03/2019
 */

public class QRCodeScanningDialog extends DialogFragment {

    private QRCodeScanDialogCallback qrCodeScanDialogCallback;
    private QRCodeScannerView qrCodeScannerView;
    private DialogInterface dialogInterface;

    public QRCodeScanningDialog() {
        dialogInterface = new DialogInterface() {
            @Override
            public void cancel() {
                QRCodeScanningDialog.this.dismiss();
            }

            @Override
            public void dismiss() {
                QRCodeScanningDialog.this.dismiss();
            }
        };
    }

    public void setOnQRRecognisedListener(@NonNull QRCodeScanDialogCallback qrCodeScanDialogCallback) {
        this.qrCodeScanDialogCallback = qrCodeScanDialogCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.dialog_start_send_mode, null))
                .setTitle(R.string.qr_code_scanning_dialog_title)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (qrCodeScanDialogCallback != null) {
                            qrCodeScanDialogCallback.onCancelClicked(dialogInterface);
                        }
                    }
                });


        AlertDialog dialog = builder.create();
        qrCodeScannerView = getView().findViewById(R.id.bsv_qrCodeScanningDialog_barcodeScanner);

        qrCodeScannerView.addOnBarcodeRecognisedListener(new QRCodeScannerView.OnQRRecognisedListener() {
            @Override
            public void onBarcodeRecognised(SparseArray<Barcode> recognisedItems) {
                if (qrCodeScanDialogCallback != null) {
                    qrCodeScanDialogCallback.qrCodeScanned(recognisedItems, dialogInterface);
                }
            }
        });

        setCancelable(false);

        return dialog;
    }

    public interface QRCodeScanDialogCallback {

        void qrCodeScanned(@NonNull SparseArray<Barcode> qrCodeResult, @NonNull DialogInterface dialogInterface);

        void onCancelClicked(DialogInterface dialogInterface);
    }
}