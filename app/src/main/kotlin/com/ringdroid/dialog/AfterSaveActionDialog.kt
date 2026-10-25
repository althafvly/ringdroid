package com.ringdroid.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import com.ringdroid.databinding.AfterSaveActionBinding
import com.ringdroid.util.createDialog

/** Offers to make a saved ringtone the default, assign it to a contact, or do nothing. */
class AfterSaveActionDialog(
    context: Context,
    private val listener: (Int) -> Unit,
) {
    private val dialog: AlertDialog

    init {
        val binding = AfterSaveActionBinding.inflate(LayoutInflater.from(context))
        binding.buttonMakeDefault.setOnClickListener { closeAndSendResult(ACTION_MAKE_DEFAULT) }
        binding.buttonChooseContact.setOnClickListener { closeAndSendResult(ACTION_CHOOSE_CONTACT) }
        binding.buttonDoNothing.setOnClickListener { closeAndSendResult(ACTION_DO_NOTHING) }
        dialog = createDialog(context, binding.root)
    }

    private fun closeAndSendResult(action: Int) {
        dialog.dismiss()
        listener(action)
    }

    fun show() {
        dialog.show()
    }

    companion object {
        const val ACTION_MAKE_DEFAULT = 1
        const val ACTION_CHOOSE_CONTACT = 2
        const val ACTION_DO_NOTHING = 3
    }
}
