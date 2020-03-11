package io.simplelogin.android.module.login

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.simplelogin.android.R
import io.simplelogin.android.databinding.ActivityVerificationBinding
import io.simplelogin.android.utils.SLApiService
import io.simplelogin.android.utils.baseclass.BaseAppCompatActivity
import io.simplelogin.android.utils.enums.SLError
import io.simplelogin.android.utils.enums.VerificationMode
import io.simplelogin.android.utils.extension.toastError

class VerificationActivity : BaseAppCompatActivity() {
    companion object {
        const val MFA_MODE = "mfaMode"
        const val ACCOUNT_ACTIVATION_MODE = "accountActivationMode"
        const val API_KEY = "apiKey"
        const val ACCOUNT = "account"
    }

    private lateinit var binding: ActivityVerificationBinding
    private lateinit var verificationMode: VerificationMode
    private val dash = "-"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        verificationMode = getVerificationMode()

        when (verificationMode) {
            is VerificationMode.Mfa -> binding.toolbarTitleText.text = "Enter OTP"
            is VerificationMode.AccountActivation -> binding.toolbarTitleText.text =
                "Enter activation code"
        }

        reset()
        setUpClickListeners()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            overridePendingTransition(R.anim.stay_still, R.anim.slide_out_down)
        }
    }

    override fun onResume() {
        super.onResume()
        val clipboardManager = (getSystemService(Context.CLIPBOARD_SERVICE) ?: return) as ClipboardManager
        val clip = clipboardManager.primaryClip ?: return
        val item = clip.getItemAt(0) ?: return
        val copiedCharacterSequence = item.text ?: return

        if (copiedCharacterSequence.count() != 6) return

        val copiedString = copiedCharacterSequence.toString()
        copiedString.toIntOrNull() ?: return

        copiedCharacterSequence.asIterable().forEach { char ->
            addNumber(char.toString())
        }

        verify(copiedString)
    }

    private fun getVerificationMode(): VerificationMode {
        val mfa = intent.getParcelableExtra<VerificationMode.Mfa>(MFA_MODE)

        if (mfa != null) return mfa

        val accountActivation = intent.getParcelableExtra<VerificationMode.AccountActivation>(
            ACCOUNT_ACTIVATION_MODE
        )

        if (accountActivation != null) return accountActivation

        throw IllegalStateException("VerificatioMode not found in intent")
    }

    private fun setUpClickListeners() {
        binding.zeroButton.setOnClickListener { addNumber("0") }
        binding.oneButton.setOnClickListener { addNumber("1") }
        binding.twoButton.setOnClickListener { addNumber("2") }
        binding.threeButton.setOnClickListener { addNumber("3") }
        binding.fourButton.setOnClickListener { addNumber("4") }
        binding.fiveButton.setOnClickListener { addNumber("5") }
        binding.sixButton.setOnClickListener { addNumber("6") }
        binding.sevenButton.setOnClickListener { addNumber("7") }
        binding.eightButton.setOnClickListener { addNumber("8") }
        binding.nineButton.setOnClickListener { addNumber("9") }
        binding.cancelButton.setOnClickListener { finish() }
        binding.deleteButton.setOnClickListener { deleteLastNumber() }
    }

    private fun addNumber(number: String) {
        if (binding.firstNumberTextView.text == dash) {
            binding.firstNumberTextView.text = number
            showError(false)
        } else if (binding.secondNumberTextView.text == dash) {
            binding.secondNumberTextView.text = number
        } else if (binding.thirdNumberTextView.text == dash) {
            binding.thirdNumberTextView.text = number
        } else if (binding.fourthNumberTextView.text == dash) {
            binding.fourthNumberTextView.text = number
        } else if (binding.fifthNumberTextView.text == dash) {
            binding.fifthNumberTextView.text = number
        } else if (binding.sixthNumberTextView.text == dash) {
            binding.sixthNumberTextView.text = number
            var code = ""
            code += binding.firstNumberTextView.text
            code += binding.secondNumberTextView.text
            code += binding.thirdNumberTextView.text
            code += binding.fourthNumberTextView.text
            code += binding.fifthNumberTextView.text
            code += binding.sixthNumberTextView.text

            verify(code)
        }
    }

    private fun deleteLastNumber() {
        if (binding.sixthNumberTextView.text != dash) {
            binding.sixthNumberTextView.text = dash
        } else if (binding.fifthNumberTextView.text != dash) {
            binding.fifthNumberTextView.text = dash
        } else if (binding.fourthNumberTextView.text != dash) {
            binding.fourthNumberTextView.text = dash
        } else if (binding.thirdNumberTextView.text != dash) {
            binding.thirdNumberTextView.text = dash
        } else if (binding.secondNumberTextView.text != dash) {
            binding.secondNumberTextView.text = dash
        } else if (binding.firstNumberTextView.text != dash) {
            binding.firstNumberTextView.text = dash
        }
    }

    private fun reset() {
        binding.firstNumberTextView.text = dash
        binding.secondNumberTextView.text = dash
        binding.thirdNumberTextView.text = dash
        binding.fourthNumberTextView.text = dash
        binding.fifthNumberTextView.text = dash
        binding.sixthNumberTextView.text = dash
    }

    private fun showError(showing: Boolean, errorMessage: String? = null) {
        val errorTextView = binding.errorTextView
        when (showing) {
            true -> {
                errorTextView.text = errorMessage
                errorTextView.alpha = 1f
                //shake()
            }

            false -> {
                errorTextView.alpha = 0f
                //animate alpha
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        when (loading) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rootLinearLayout.alpha = 0.3f
            }

            false -> {
                binding.progressBar.visibility = View.GONE
                binding.rootLinearLayout.alpha = 1f
            }
        }
    }

    private fun verify(code: String) {
        val deviceName = Build.DEVICE
        when (verificationMode) {
            is VerificationMode.Mfa -> {
                setLoading(true)
                SLApiService.verifyMfa(
                    (verificationMode as VerificationMode.Mfa).mfaKey,
                    code,
                    deviceName
                ) { apiKey, error ->
                    runOnUiThread {
                        setLoading(false)
                        if (error != null) {
                            showError(true, error.description)
                            reset()
                        } else if (apiKey != null) {
                            val returnIntent = Intent()
                            returnIntent.putExtra(API_KEY, apiKey.value)
                            setResult(Activity.RESULT_OK, returnIntent)
                            finish()
                        }
                    }
                }
            }

            is VerificationMode.AccountActivation -> {
                setLoading(true)
                SLApiService.verifyEmail(
                    (verificationMode as VerificationMode.AccountActivation).email,
                    code
                ) { error ->
                    runOnUiThread {
                        setLoading(false)
                        if (error != null) {
                            when (error) {
                                is SLError.ReactivationNeeded -> showReactivationAlert((verificationMode as VerificationMode.AccountActivation).email)

                                else -> {
                                    showError(true, error.description)
                                    reset()
                                }
                            }

                        } else {
                            val returnIntent = Intent()
                            returnIntent.putExtra(
                                ACCOUNT,
                                (verificationMode as VerificationMode.AccountActivation)
                            )
                            setResult(Activity.RESULT_OK, returnIntent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun showReactivationAlert(email: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Wrong code too many times")
            .setMessage("Resend a new activation code for \"$email\"")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Resend me new code") { _, _ ->
                requestNewCode(email)
                reset()
            }
            .show()
    }

    private fun requestNewCode(email: String) {
        setLoading(true)
        SLApiService.reactivate(email) {error ->
            runOnUiThread {
                setLoading(false)
                if (error != null) {
                    toastError(error)
                } else {
                    Toast.makeText(this, "Check your inbox for new activation code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}