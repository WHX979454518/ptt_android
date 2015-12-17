package com.xianzhitech.ptt.ui.home;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;

/**
 *
 * 提供一个警告对话框
 *
 * Created by fanchao on 17/12/15.
 */
public class AlertDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {


    public static final String ARG_TITLE = "arg_title";
    public static final String ARG_MESSAGE = "arg_message";
    public static final String ARG_BTN_POSITIVE = "arg_btn_positive";
    public static final String ARG_BTN_NEGATIVE = "arg_btn_negative";
    public static final String ARG_BTN_NEUTRAL = "arg_btn_natural";

    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        return new AlertDialog.Builder(getContext())
                .setTitle(getArguments().getCharSequence(ARG_TITLE))
                .setMessage(getArguments().getCharSequence(ARG_MESSAGE))
                .setPositiveButton(getArguments().getCharSequence(ARG_BTN_POSITIVE), this)
                .setNegativeButton(getArguments().getCharSequence(ARG_BTN_NEGATIVE), this)
                .setNeutralButton(getArguments().getCharSequence(ARG_BTN_NEUTRAL), this)
                .create();
    }

    private <T> T getParentAs(final Class<T> clazz) {
        if (clazz.isInstance(getParentFragment())) {
            return (T) getParentFragment();
        } else if (clazz.isInstance(getActivity())) {
            return (T) getActivity();
        } else {
            return null;
        }
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE: {
                final OnPositiveButtonClickListener callback = getParentAs(OnPositiveButtonClickListener.class);
                if (callback != null) {
                    callback.onPositiveButtonClicked(this);
                }
                break;
            }

            case DialogInterface.BUTTON_NEGATIVE: {
                final OnNegativeButtonClickListener callback = getParentAs(OnNegativeButtonClickListener.class);
                if (callback != null) {
                    callback.onNegativeButtonClicked(this);
                }
                break;
            }

            case DialogInterface.BUTTON_NEUTRAL: {
                final OnNeutralButtonClickListener callback = getParentAs(OnNeutralButtonClickListener.class);
                if (callback != null) {
                    callback.onNeutralButtonClicked(this);
                }
                break;
            }
        }
    }

    public interface OnPositiveButtonClickListener {
        void onPositiveButtonClicked(AlertDialogFragment fragment);
    }

    public interface OnNegativeButtonClickListener {
        void onNegativeButtonClicked(AlertDialogFragment fragment);
    }

    public interface OnNeutralButtonClickListener {
        void onNeutralButtonClicked(AlertDialogFragment fragment);
    }

    public static class Builder {
        private CharSequence title;
        private CharSequence message;
        private CharSequence btnPositive;
        private CharSequence btnNegative;
        private CharSequence btnNeutral;

        public Builder setTitle(final CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(final CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setBtnPositive(final CharSequence btnPositive) {
            this.btnPositive = btnPositive;
            return this;
        }

        public Builder setBtnNegative(final CharSequence btnNegative) {
            this.btnNegative = btnNegative;
            return this;
        }

        public Builder setBtnNeutral(final CharSequence btnNeutral) {
            this.btnNeutral = btnNeutral;
            return this;
        }

        public AlertDialogFragment create() {
            final AlertDialogFragment fragment = new AlertDialogFragment();
            final Bundle args = new Bundle();
            args.putCharSequence(ARG_TITLE, title);
            args.putCharSequence(ARG_MESSAGE, message);
            args.putCharSequence(ARG_BTN_POSITIVE, btnPositive);
            args.putCharSequence(ARG_BTN_NEGATIVE, btnNegative);
            args.putCharSequence(ARG_BTN_NEUTRAL, btnNeutral);
            fragment.setArguments(args);
            return fragment;
        }

        public void show(FragmentManager fragmentManager, String tag) {
            create().show(fragmentManager, tag);
        }

    }
}
