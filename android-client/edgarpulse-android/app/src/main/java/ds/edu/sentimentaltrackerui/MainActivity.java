package ds.edu.sentimentaltrackerui;
/*
  Author: Xinyu Long
  File: MainActivity.java
  Description: MainActivity serves as the entry point for the Android application,
  providing a user interface for inputting CIK numbers and displaying risk tone analysis results.
 */
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private EditText inputCik; // Input field for CIK number
    private Button btnAnalyze; // Button to trigger analysis
    private LinearLayout resultContainer; // Container for displaying results
    private TextView tvCompanyName; // TextView for displaying company name
    private TextView tvIndustry; // textView for displaying industry
    private TextView tvFormType; // textView for displaying analyzed form type
    private TextView tvRiskScore; // textView for displaying risk tone score
    private TextView tvRiskLabel; // textView for displaying risk tone label
    private TextView tvActivityLevel; // textView for displaying disclosure activity level
    private TextView tvSummary; // textView for displaying summary of risk tone analysis

    // Public backend endpoint for demo deployment
    private static final String BACKEND_BASE_URL =
            "https://glowing-trout-4j94wv6wp6qph5rqw-8080.app.github.dev/analyze?cik=";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Ctreate UI and set up event listener for the analyze button
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputCik = findViewById(R.id.inputCik);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        resultContainer = findViewById(R.id.resultContainer);

        tvCompanyName = findViewById(R.id.tvCompanyName);
        tvIndustry = findViewById(R.id.tvIndustry);
        tvFormType = findViewById(R.id.tvFormType);
        tvRiskScore = findViewById(R.id.tvRiskScore);
        tvRiskLabel = findViewById(R.id.tvRiskLabel);
        tvActivityLevel = findViewById(R.id.tvActivityLevel);
        tvSummary = findViewById(R.id.tvSummary);

        btnAnalyze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cik = inputCik.getText().toString().trim();

                // Client-side validation
                if (cik.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter a CIK.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!cik.matches("\\d+")) {
                    Toast.makeText(MainActivity.this, "CIK must contain digits only.", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnAnalyze.setText("Analyzing...");
                btnAnalyze.setEnabled(false);
                resultContainer.setVisibility(View.GONE);

                fetchRiskAnalysisData(cik);
            }
        });
    }

    // Fetch risk analysis data from the backend server using the provided CIK
    private void fetchRiskAnalysisData(String cik) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;

                try {
                    URL url = new URL(BACKEND_BASE_URL + cik);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    int responseCode = connection.getResponseCode();

                    BufferedReader reader;
                    if (responseCode >= 200 && responseCode < 300) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    } else {
                        reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    }

                    StringBuilder responseText = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseText.append(line);
                    }
                    reader.close();

                    JSONObject jsonObject = new JSONObject(responseText.toString());

                    boolean success = jsonObject.optBoolean("success", false);
                    if (!success) {
                        String errorMessage = jsonObject.optString("error", "Unknown server error.");
                        showError(errorMessage);
                        return;
                    }

                    String companyName = jsonObject.optString("companyName", "N/A");
                    String industry = jsonObject.optString("industry", "N/A");
                    String formType = jsonObject.optString("analyzedFormType", "N/A");
                    int riskScore = jsonObject.optInt("riskToneScore", -1);
                    String riskLabel = jsonObject.optString("riskToneLabel", "N/A");
                    String activityLevel = jsonObject.optString("activityLevel", "N/A");
                    String summary = jsonObject.optString("summary", "No summary available.");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateUI(companyName, industry, formType, riskScore, riskLabel, activityLevel, summary);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    showError("Network or parsing error: " + e.getMessage());
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    // Update the UI with the fetched data and apply color coding based on risk score
    private void updateUI(String companyName,
                          String industry,
                          String formType,
                          int riskScore,
                          String riskLabel,
                          String activityLevel,
                          String summary) {

        tvCompanyName.setText(companyName);
        tvIndustry.setText("Industry: " + industry);
        tvFormType.setText("Analyzed Form: " + formType);
        tvRiskScore.setText(String.valueOf(riskScore));
        tvRiskLabel.setText(riskLabel);
        tvActivityLevel.setText("Disclosure Activity: " + activityLevel);
        tvSummary.setText(summary);

        // Color by risk score
        if (riskScore >= 65) {
            tvRiskScore.setTextColor(Color.parseColor("#D32F2F"));
            tvRiskLabel.setTextColor(Color.parseColor("#D32F2F"));
        } else if (riskScore >= 35) {
            tvRiskScore.setTextColor(Color.parseColor("#F57C00"));
            tvRiskLabel.setTextColor(Color.parseColor("#F57C00"));
        } else {
            tvRiskScore.setTextColor(Color.parseColor("#388E3C"));
            tvRiskLabel.setTextColor(Color.parseColor("#388E3C"));
        }

        btnAnalyze.setText("Analyze Risk Tone");
        btnAnalyze.setEnabled(true);
        resultContainer.setVisibility(View.VISIBLE);
    }

    // Show error messages and reset the analyze button state
    private void showError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                btnAnalyze.setText("Analyze Risk Tone");
                btnAnalyze.setEnabled(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}