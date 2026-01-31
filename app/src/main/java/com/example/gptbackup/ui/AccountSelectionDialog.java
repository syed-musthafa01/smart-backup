package com.example.gptbackup.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class AccountSelectionDialog {

    private static final String PREF_NAME = "account_prefs";
    private static final String KEY_SELECTED_ACCOUNT = "selected_account";

    public static void show(Context context) {

        AccountManager accountManager = AccountManager.get(context);
        Account[] accounts = accountManager.getAccountsByType("com.google");

        if (accounts.length == 0) {
            new AlertDialog.Builder(context)
                    .setTitle("No Google Account")
                    .setMessage("Please add a Google account in device settings.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        List<String> emails = new ArrayList<>();
        for (Account acc : accounts) {
            emails.add(acc.name);
        }

        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String saved = prefs.getString(KEY_SELECTED_ACCOUNT, emails.get(0));
        int checkedIndex = emails.indexOf(saved);
        if (checkedIndex == -1) checkedIndex = 0;

        new AlertDialog.Builder(context)
                .setTitle("Select Google Account")
                .setSingleChoiceItems(
                        emails.toArray(new String[0]),
                        checkedIndex,
                        (dialog, which) -> {
                            prefs.edit()
                                    .putString(KEY_SELECTED_ACCOUNT, emails.get(which))
                                    .apply();
                            dialog.dismiss();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 🔹 Helper for later (Drive upload)
    public static String getSelectedAccount(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_ACCOUNT, null);
    }
}
