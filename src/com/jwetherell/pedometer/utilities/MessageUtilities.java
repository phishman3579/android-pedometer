package com.jwetherell.pedometer.utilities;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;


/**
 * This abstract class provides static methods to display messages.
 * 
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public abstract class MessageUtilities {

    /** Enum representing the confirmation result */
    public static enum ConfirmationResult {
        YES, NO
    };

    /**
     * Show confirmation box using AlertDialog.
     * 
     * @param context
     *            Context to show the alert.
     * @param msg
     *            String to show in the alert.
     * @param yesClick
     *            OnClickListener to execute on a YES click.
     * @param noClick
     *            OnClickListener to execute on a NO click.
     * @throws NullPointerException
     *             if context or msg are NULL.
     */
    public static void confirmUser(Context context, String msg, DialogInterface.OnClickListener yesClick, DialogInterface.OnClickListener noClick) {
        if (context == null || msg == null) throw new NullPointerException();

        Builder alert = new AlertDialog.Builder(context);
        alert.setIcon(android.R.drawable.ic_dialog_alert).setTitle("Confirmation").setMessage(msg).setPositiveButton("Yes", yesClick)
                .setNegativeButton("No", noClick).show();
    }
}
