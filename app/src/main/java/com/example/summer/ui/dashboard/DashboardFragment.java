package com.example.summer.ui.dashboard;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;
import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
//import androidx.appcompat.app.AlertDialog;
import com.example.summer.R;
import com.example.summer.utils.NetworkUtils;
import com.example.summer.utils.WenXin;
import com.example.summer.DemoApplication;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.widget.TextView;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DashboardFragment extends Fragment {

    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private EditText addressSearchEditText;
    private Button searchButton;
    public LocationClient mLocationClient;
    private MyLocationListener myListener;
    private Button routePlanningButton;

    // 用于存储途径点
    private List<LatLng> midPoints = new ArrayList<>();
    private LatLng startPoint;
    private LatLng endPoint;

    private boolean flag1=false;//用于判断路径规划是否有起点

    private DemoApplication demoApplication; // 新增：持有应用实例

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);
        // 初始化应用实例（在视图创建时获取）
        demoApplication = (DemoApplication) requireActivity().getApplication();

        mMapView = root.findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMyLocationEnabled(true);

        // 初始化地图中心点
        LatLng chengdeSummerResort = new LatLng(40.9978, 117.9413);
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(chengdeSummerResort, 15.8f));

        // 初始化搜索组件
        addressSearchEditText = root.findViewById(R.id.address_search_edit_text);
        searchButton = root.findViewById(R.id.search_button);

        searchButton.setOnClickListener(v -> {
            String address = addressSearchEditText.getText().toString().trim();
            if (!address.isEmpty()) {
                // 新增：搜索前先更新搜索次数
                demoApplication.increaseSearchTimes(address); // 直接通过应用实例调用
                new WenXinTask().execute(address);

            } else {
                Toast.makeText(requireContext(), "请输入地址", Toast.LENGTH_SHORT).show();
            }
        });

        // 初始化定位
        initLocation();

        // 初始化路线规划按钮
        routePlanningButton = root.findViewById(R.id.route_planning_button);
        routePlanningButton.setOnClickListener(v -> {

            if (startPoint == null) {
                // 当 startPoint 为空时执行的代码块
                // 例如，可以在这里进行相应的提示或处理逻辑
                Toast.makeText(requireContext(), "确定起点", Toast.LENGTH_SHORT).show();
                showStartPointDialog();
            } else if (flag1&&endPoint==null){
                Toast.makeText(requireContext(), "确定终点", Toast.LENGTH_SHORT).show();
                showEndPointDialog();
            }else if(endPoint!=null) {
                Toast.makeText(requireContext(), "路线规划显示", Toast.LENGTH_SHORT).show();
                showRoutePlanningResultDialog();
            } else {
                Toast.makeText(requireContext(), "确定途经点", Toast.LENGTH_SHORT).show();
                showMidPointDialog();
            }


        });

        return root;
    }


    private void showStartPointDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_layout, null);
        EditText startEditText = view.findViewById(R.id.start_edit_text);
        Button confirmButton = view.findViewById(R.id.confirm_start_button);

        builder.setView(view);
        builder.setTitle("路线规划");
        builder.setMessage("请确定起点");

        AlertDialog dialog = builder.create();

        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String startAddress = startEditText.getText().toString().trim();
                if (!TextUtils.isEmpty(startAddress)) {
                    // 新增：起点搜索时更新搜索次数
                    demoApplication.increaseSearchTimes(startAddress);
                    NetworkUtils.getLocationFromAddress(startAddress, new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "请求失败：" + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                String json = response.body().string();
                                parseJSONResponse(json);
                                dialog.dismiss();
                                //showMidPointDialog(); // 这里调用了 showMidPointDialog 方法

                            } else {
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), "请求失败，状态码：" + response.code(), Toast.LENGTH_SHORT).show()
                                );
                                dialog.dismiss();
                            }
                        }
                    });

                } else {
                    Toast.makeText(requireContext(), "请输入起点地址", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }

            }
        });
        dialog.show();

    }

    private void showMidPointDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_layout_route, null);
        EditText midEditText = view.findViewById(R.id.midpoint_edit_text);
        Button addButton = view.findViewById(R.id.add_midpoint_button);
        Button confirmEndButton = view.findViewById(R.id.confirm_endpoint_button);

        builder.setView(view);
        builder.setTitle("路线规划");
        builder.setMessage("请确定打算途径的地点");

        AlertDialog dialog = builder.create();

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String midAddress = midEditText.getText().toString().trim();
                if (!TextUtils.isEmpty(midAddress)) {
                    // 新增：途径点搜索时更新搜索次数
                    demoApplication.increaseSearchTimes(midAddress);
                    // 获取经纬度
                    NetworkUtils.getLocationFromAddress(midAddress, new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "请求失败：" + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                String json = response.body().string();
                                parseJSONResponse(json);


                            } else {
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), "请求失败，状态码：" + response.code(), Toast.LENGTH_SHORT).show()
                                );
                            }
                        }
                    });
                } else {
                    Toast.makeText(requireContext(), "请输入途径地点", Toast.LENGTH_SHORT).show();
                }

            }
        });

        confirmEndButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flag1=true;
                dialog.dismiss();

            }

        });

        dialog.show();
    }

    private void showEndPointDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_layout_endpoint, null);
        EditText endEditText = view.findViewById(R.id.endpoint_edit_text);
        Button confirmButton = view.findViewById(R.id.confirm_endpoint_final_button);

        builder.setView(view);
        builder.setTitle("确定终点");
        builder.setMessage("请输入终点地址");

        AlertDialog dialog = builder.create();

        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String endAddress = endEditText.getText().toString().trim();
                if (!TextUtils.isEmpty(endAddress)) {
                    // 新增：终点搜索时更新搜索次数
                    demoApplication.increaseSearchTimes(endAddress);
                    // 获取经纬度
                    NetworkUtils.getLocationFromAddress(endAddress, new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "请求失败：" + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                String json = response.body().string();
                                parseJSONResponse(json);

                            } else {
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), "请求失败，状态码：" + response.code(), Toast.LENGTH_SHORT).show()
                                );
                            }
                        }
                    });
                } else {
                    Toast.makeText(requireContext(), "请输入终点地址", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            }
        });

        dialog.show();
    }
    private void showRoutePlanningResultDialog() {
        if (startPoint != null && endPoint != null) {
            double distance = calculateDistance(startPoint, endPoint);
            String resultMessage = "路线规划完成，距离为：" + String.format("%.2f", distance) + " 千米";

            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_route_result_layout, null);
            TextView resultTextView = view.findViewById(R.id.result_text_view);
            Button cleanButton = view.findViewById(R.id.clean_button);
            Button okButton = view.findViewById(R.id.ok_button);

            resultTextView.setText(resultMessage);

            builder.setView(view);
            builder.setTitle("路线规划结果");

            AlertDialog dialog = builder.create();

            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            cleanButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 清空起点
                    startPoint = null;
                    // 清空终点
                    endPoint = null;
                    if (midPoints != null) {
                        midPoints.clear();
                    }
                    // 这里假设你还有其他标记相关的变量，比如 flag1 等，也一并清空
                    flag1 = false;
                    // 假设 mMapView 是百度地图的 MapView 对象
                    mMapView.setVisibility(View.GONE);
                    mMapView.setVisibility(View.VISIBLE);
                    dialog.dismiss();
                }
            });
            dialog.show();
        }
    }

    private double calculateDistance(LatLng start, LatLng end) {
        double lat1 = Math.toRadians(start.latitude);
        double lon1 = Math.toRadians(start.longitude);
        double lat2 = Math.toRadians(end.latitude);
        double lon2 = Math.toRadians(end.longitude);

        double dlon = lon2 - lon1;
        double dlat = lat2 - lat1;
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // 地球平均半径约为 6371 千米，返回距离
    }

    private void parseJSONResponse(String json) {
        try {
            int start = json.indexOf("{");
            int end = json.lastIndexOf("}");
            if (start != -1 && end != -1) {
                json = json.substring(start, end + 1);
            }

            JSONObject jsonObject = new JSONObject(json);
            int status = jsonObject.getInt("status");
            if (status == 0) {
                JSONObject result = jsonObject.getJSONObject("result");
                JSONObject location = result.getJSONObject("location");
                double lat = location.getDouble("lat");
                double lng = location.getDouble("lng");
                LatLng latLng = new LatLng(lat, lng);

                // 根据调用的函数确定存储的变量
                if (startPoint == null) {
                    startPoint = latLng;
                } else if(flag1){
                    endPoint = latLng;
                }else{
                    midPoints.add(latLng);
                }

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "定位成功：" + latLng.toString(), Toast.LENGTH_SHORT).show();
                    mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 15.8f));

                    BitmapDescriptor bitmap = BitmapDescriptorFactory.fromResource(android.R.drawable.ic_dialog_map);
                    OverlayOptions option = new MarkerOptions()
                            .position(latLng)
                            .icon(bitmap);
                    mBaiduMap.addOverlay(option);
                });

            } else {
                String message = jsonObject.getString("message");
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "请求失败：" + message, Toast.LENGTH_SHORT).show()
                );
            }
        } catch (JSONException e) {
            e.printStackTrace();
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "解析失败：" + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void showIntroductionDialog(String introduction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_location_introduction, null);
        builder.setView(view);

        TextView locationIntroductionText = view.findViewById(R.id.location_introduction_text);
        if (introduction != null &&!introduction.isEmpty()) {
            locationIntroductionText.setText(introduction);
        } else {
            locationIntroductionText.setText("无介绍内容");

        }

        builder.setPositiveButton("确定", (dialog, which) -> dialog.dismiss());
        builder.setTitle("简介");

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void initLocation() {
        try {
            mLocationClient = new LocationClient(getActivity().getApplicationContext());
            myListener = new MyLocationListener();
            mLocationClient.registerLocationListener(myListener);

            LocationClientOption option = new LocationClientOption();
            option.setIsNeedAddress(true);
            mLocationClient.setLocOption(option);

            if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            } else {
                mLocationClient.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 其他生命周期方法保持不变
    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLocationClient.stop();
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
    }

    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null || mMapView == null) return;
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    .direction(location.getDirection())
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .build();
            mBaiduMap.setMyLocationData(locData);
        }
    }

    private class WenXinTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String address = params[0];
            WenXin wenXin = new WenXin();
            try {
                return wenXin.getLocationIntroduction(address);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                showIntroductionDialog(result);
            } else {
                Log.e("WenXin", "请求失败");
                Toast.makeText(requireContext(), "请求失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
}