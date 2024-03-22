package com.example.opendata0305;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import android.Manifest;

import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private String csvDataUrl = "https://data.ntpc.gov.tw/api/datasets/3a43d9d0-bc7f-4f8f-9940-080005f6ac8c/csv/file";
    private Spinner areaSpinner;
    private List<Marker> markerList = new ArrayList<>();
    private ProgressDialog progressDialog;
    private AlertDialog.Builder alertDialogBuilder;
    private FusedLocationProviderClient fusedLocationClient;
    private final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化並詢問使用者選擇地區
        alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("請選擇地區：");

        areaSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.areas_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        areaSpinner.setAdapter(adapter);

        alertDialogBuilder.setView(areaSpinner);

        alertDialogBuilder.setPositiveButton("確定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String selectedArea = areaSpinner.getSelectedItem().toString();
                // 读取并处理 CSV 文件
                loadCSVData(selectedArea); // 加載 CSV 數據
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();

        // 獲取重新選擇按鈕並設置點擊監聽器
        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 清除所有標記
                clearMarkers();
                clearRoute();
                // 顯示地區選擇對話框
                alertDialog.show();
            }
        });

        // 獲取搜索最近標記按鈕並設置點擊監聽器
        Button searchNearestButton = findViewById(R.id.searchNearestButton);
        searchNearestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 搜索最近標記
                searchNearestMarker();
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng defaultLocation = new LatLng(25.032969, 121.565418);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 8));

        // 檢查位置權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // 獲取用戶位置並搜索最近標記
            getLastKnownLocationAndSearchNearestMarker();
        }
    }


    private void loadCSVData(final String selectedArea) {
        // 清除之前的標記
        clearMarkers();
        clearRoute();
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("加載中!請稍後...");
        progressDialog.setCancelable(false); // 設置不可取消
        progressDialog.show();

        new LoadCSVDataTask().execute(selectedArea);
    }



    private class LoadCSVDataTask extends AsyncTask<String, Void, List<MarkerOptions>> {
        @Override
        protected List<MarkerOptions> doInBackground(String... params) {
            List<MarkerOptions> markerOptionsList = new ArrayList<>();
            try {
                String selectedArea = params[0];
                URL url = new URL(csvDataUrl);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = urlConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String[] columns = line.split(",");
                    if (columns.length >= 5) { // 確保至少有五列數據
                        String area = columns[2].trim();
                        String addressWithArea = area + columns[4].trim(); // 地區和地址拼接
                        if (area.equals(selectedArea)) {
                            LatLng latLng = getLocationFromAddress(addressWithArea);
                            if (latLng != null) {
                                String name = columns[3].trim(); // 名稱在 CSV 文件的第四列
                                Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wc_icon);
                                Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 50, 60, false);
                                MarkerOptions markerOptions = new MarkerOptions()
                                        .position(latLng)
                                        .title(name) // 設置標記的名稱
                                        .icon(BitmapDescriptorFactory.fromBitmap(resizedBitmap));

                                markerOptionsList.add(markerOptions);
                            }
                        }
                    }
                }
                bufferedReader.close();
                inputStream.close();
                urlConnection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return markerOptionsList;
        }

        @Override
        protected void onPostExecute(List<MarkerOptions> markerOptionsList) {
            super.onPostExecute(markerOptionsList);

            if (!markerOptionsList.isEmpty()) {
                // 獲取第一個標記的位置
                LatLng firstMarkerPosition = markerOptionsList.get(0).getPosition();

                // 設置新的標記
                for (MarkerOptions options : markerOptionsList) {
                    Marker marker = mMap.addMarker(options);
                    markerList.add(marker);
                }

                // 隱藏進度對話框
                progressDialog.dismiss();
                // 顯示加載完成通知
                Snackbar.make(findViewById(android.R.id.content), "加載完成!", Snackbar.LENGTH_SHORT).show();

                // 將地圖移動到第一個標記的位置並縮放
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstMarkerPosition, 13)); // 設置縮放級別為12，可以根據需要調整
            } else {
                // 如果標記列表為空，顯示提示信息
                Snackbar.make(findViewById(android.R.id.content), "沒有找到標記!", Snackbar.LENGTH_SHORT).show();
            }
        }

    }

    // 清除所有標記
    private void clearMarkers() {
        for (Marker marker : markerList) {
            marker.remove();
        }
        markerList.clear();
    }
    private void clearRoute() {
        mMap.clear(); // 清除地图上的所有覆盖物，包括标记和路线
        // 重新添加用户位置标记（如果需要）
        getLastKnownLocationAndSearchNearestMarker();
    }


    private LatLng getLocationFromAddress(String locationName) {
        Geocoder geocoder = new Geocoder(MainActivity.this);
        List<Address> addresses;
        try {
            addresses = geocoder.getFromLocationName(locationName, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                return new LatLng(address.getLatitude(), address.getLongitude());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void searchNearestMarker() {
        if (mMap != null) {
            LatLng userLocation = getCurrentUserLocation();
            LatLng nearestMarkerLocation = findNearestMarkerLocation(userLocation);
            if (userLocation != null && nearestMarkerLocation != null) {
                // 绘制路线
                drawRoute(userLocation, nearestMarkerLocation);
                // 创建一个 LatLngBounds 对象，将用户位置和最近标记位置包含在内
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(userLocation);
                builder.include(nearestMarkerLocation);
                LatLngBounds bounds = builder.build();

                // 计算地图视窗的尺寸
                int padding = 120; // 像素

                // 移动地图视角至包含用户位置和最近标记位置的范围
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            }
        }
    }


    private LatLng getCurrentUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                // 獲取到了用戶的位置信息
                                double latitude = location.getLatitude();
                                double longitude = location.getLongitude();
                                LatLng userLatLng = new LatLng(latitude, longitude);
                                searchNearestMarker(userLatLng); // 搜索最近的標記（廁所）
                            }
                        }
                    });
        }
        return null;
    }


    // 在搜索最近標記後顯示路線
    private void searchNearestMarker(LatLng userLocation) {
        if (mMap != null) {
            LatLng nearestMarkerLocation = findNearestMarkerLocation(userLocation);
            if (userLocation != null && nearestMarkerLocation != null) {
                // 绘制路线
                drawRoute(userLocation, nearestMarkerLocation);
            }
        }
    }



    private LatLng findNearestMarkerLocation(LatLng userLocation) {
        if (userLocation == null) {
            return null;
        }

        Marker nearestMarker = null;
        float minDistance = Float.MAX_VALUE;
        LatLng nearestMarkerLocation = null;

        for (Marker marker : markerList) {
            float[] distanceResult = new float[1];
            LatLng markerPosition = marker.getPosition();
            if (markerPosition != null) {
                // 计算用户位置与标记位置的距离
                android.location.Location.distanceBetween(userLocation.latitude, userLocation.longitude,
                        markerPosition.latitude, markerPosition.longitude, distanceResult);
                if (distanceResult[0] < minDistance) {
                    minDistance = distanceResult[0];
                    nearestMarker = marker;
                    nearestMarkerLocation = markerPosition;
                }
            }
        }

        if (nearestMarker != null) {
            // 将最近的标记（厕所）设为特别标记
            nearestMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        }

        return nearestMarkerLocation;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // 調用super方法

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用戶授予權限，獲取用戶位置並搜索最近標記
                getLastKnownLocationAndSearchNearestMarker();
            } else {
                // 用戶拒絕了權限請求，您可以做一些適當的處理
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void getLastKnownLocationAndSearchNearestMarker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 如果權限尚未授予，請求權限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                // 獲取到了用戶的位置信息
                                double latitude = location.getLatitude();
                                double longitude = location.getLongitude();
                                LatLng userLatLng = new LatLng(latitude, longitude);

                                // 創建用戶位置的標記
                                Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.user_icon);
                                Bitmap resizedUser = Bitmap.createScaledBitmap(originalBitmap, 50, 50, false);
                                MarkerOptions userMarkerOptions = new MarkerOptions()
                                        .position(userLatLng)  // 用戶位置的經緯度
                                        .title("你的位置")  // 標記的標題
                                        .icon(BitmapDescriptorFactory.fromBitmap(resizedUser));  // 設置自定義圖標

                                // 將標記添加到地圖上
                                mMap.addMarker(userMarkerOptions);

                                // 移動地圖視角至用戶位置
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 13));

                            }
                        }
                    });

        }
    }
    private void drawRoute(LatLng origin, LatLng destination) {
        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey("AIzaSyBBynoB6iXHrxM6V_oc__aeKgUUC5gMi0c")
                .build();

        DirectionsResult result;
        try {
            result = DirectionsApi.newRequest(context)
                    .mode(TravelMode.DRIVING)
                    .origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                    .destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                    .await();

            // 检查结果是否成功
            if (result != null && result.routes.length > 0) {
                List<com.google.maps.model.LatLng> path = result.routes[0].overviewPolyline.decodePath();

                PolylineOptions polylineOptions = new PolylineOptions();
                polylineOptions.color(ContextCompat.getColor(this, R.color.red)); // 设置路线颜色，使用资源文件中的颜色
                for (com.google.maps.model.LatLng latLng : path) {
                    polylineOptions.add(new LatLng(latLng.lat, latLng.lng));
                }

                // 在地图上绘制路线
                mMap.addPolyline(polylineOptions);

                // 创建一个 LatLngBounds 对象，将起点和终点包含在内
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(origin);
                builder.include(destination);
                LatLngBounds bounds = builder.build();

                // 计算地图视窗的尺寸
                int padding = 120; // 像素

                // 移动地图视角至包含起点和终点的范围
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            }
        } catch (ApiException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

}
