package com.treetime.dev.playfaturamento;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    BillingClient billingClient;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        billingClient = BillingClient.newBuilder(this)
                .enablePendingPurchases()
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null){
                            for (Purchase purchase: list){
                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED &&
                                !purchase.isAcknowledged()){
                                    verifyPurchase(purchase);
                                }
                            }
                        }
                    }
                }).build();
        connectToGooglePlayBilling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        billingClient.queryPurchasesAsync(
                BillingClient.SkuType.INAPP,
                new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                            for (Purchase purchase: list){
                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED &&
                                !purchase.isAcknowledged()){
                                    verifyPurchase(purchase);
                                }
                            }
                        }
                    }
                }
        );
    }

    private void connectToGooglePlayBilling(){
        billingClient.startConnection(
                new BillingClientStateListener() {
                    @Override
                    public void onBillingServiceDisconnected() {
                        connectToGooglePlayBilling();
                    }

                    @Override
                    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                            getProductDetails();
                        }
                    }
                }
        );
    }

    private void verifyPurchase(Purchase purchase){
        String requestUrl = "https://us-central1-playbilling-a0ec2.cloudfunctions.net/verifyPurchases?" +
                "purchaseToken=" + purchase.getPurchaseToken() + "&" +
                "purchaseTime=" + purchase.getPurchaseTime() + "&" +
                "orderId=" + purchase.getOrderId();
        Activity activity = this;
        StringRequest stringRequest = new StringRequest(
                Request.Method.POST,
                requestUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject purchaseInfoFromServer = new JSONObject(response);
                            System.out.println("TREETIME DEV INFO1: "+ purchaseInfoFromServer);
                            if (purchaseInfoFromServer.getBoolean("isValid")){
                                //Para compras recorrentes
                                ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
                                billingClient.consumeAsync(
                                        consumeParams,
                                        new ConsumeResponseListener() {
                                            @Override
                                            public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String s) {
                                                System.out.println("TREETIME DEV INFO2: "+billingResult.getResponseCode());
                                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
//                                                    Toast.makeText(activity, "Consumed!", Toast.LENGTH_LONG).show();
//                                                    System.out.println("TREETIME DEV STATUS DA COMPRA: Consumed!");
                                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Toast.makeText(activity, "Consumed!", Toast.LENGTH_LONG).show();
                                                        }
                                                    });
                                                }
                                            }
                                        }
                                );
                                //Para itens que podem ser comprados uma única vez
//                                AcknowledgePurchaseParams acknowledgePurchaseParams
//                                    = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
//                                billingClient.acknowledgePurchase(
//                                        acknowledgePurchaseParams,
//                                        new AcknowledgePurchaseResponseListener() {
//                                            @Override
//                                            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
//                                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
//                                                    Toast.makeText(activity, "ACKNOWLEDGED", Toast.LENGTH_LONG).show();
//                                                }
//                                            }
//                                        }
//                                );
                            }
                        }catch (Exception err){

                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                }
        );
        Volley.newRequestQueue(this).add(stringRequest);
    }

    private void getProductDetails(){
        List<String> productIds = new ArrayList<>();
        productIds.add("small_potion");
        SkuDetailsParams getProductDetailsQuery = SkuDetailsParams
                .newBuilder()
                .setSkusList(productIds)
                .setType(BillingClient.SkuType.INAPP)
                .build();
        Activity activity = this;
        billingClient.querySkuDetailsAsync(
                getProductDetailsQuery,
                new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> list) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                        list != null){
                            TextView itemNameTextView = findViewById(R.id.itemName);
                            Button itemPriceButton = findViewById(R.id.itemPrice);
                            SkuDetails itemInfo = list.get(0);
                            itemNameTextView.setText(itemInfo.getTitle());
                            System.out.println("TREETIME DEV RESPOSTA DA REQUISIÇÃO: "+itemInfo.getTitle());
                            itemPriceButton.setText(itemInfo.getPrice());
                            itemPriceButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    billingClient.launchBillingFlow(
                                            activity,
                                            BillingFlowParams.newBuilder().setSkuDetails(itemInfo).build()
                                    );
                                }
                            });
                        }
                    }
                }
        );
    }
}