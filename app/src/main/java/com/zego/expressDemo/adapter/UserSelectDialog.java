package com.zego.expressDemo.adapter;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zego.expressDemo.R;

import java.util.List;

public class UserSelectDialog extends Dialog implements View.OnClickListener, UserAdapter.OnUserItemClickListener {

    private UserAdapter mUserAdapter;

    private OnPickUserUpListener mOnPickUserUpListener;

    public UserSelectDialog(@NonNull Context context, List<String> userList) {
        super(context, R.style.CommonDialog);

        initData(userList);
        initView(context);
    }

    private void initData(List<String> userList) {
        mUserAdapter = new UserAdapter();
        mUserAdapter.setOnUserItemClickListener(this);

        mUserAdapter.setUserList(userList);
    }


    private void initView(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_user_select_layout, null);
        setContentView(view);
        // 设置可以取消
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        // 设置Dialog高度位置
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        // 经计算，dialog_enqueue_mode_choose_layout的高度为250dp
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.BOTTOM;
        // 设置没有边框
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(layoutParams);

        RecyclerView rvUsers = findViewById(R.id.rv_users);

        rvUsers.setAdapter(mUserAdapter);
        rvUsers.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        findViewById(R.id.tv_cancel).setOnClickListener(this);
    }

    public void setOnPickUpUserListener(OnPickUserUpListener onPickUserUpListener) {
        this.mOnPickUserUpListener = onPickUserUpListener;
    }

    public void notifyDataSetChanged() {
        mUserAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_cancel) {
            dismiss();
        }
    }

    @Override
    public void OnUserItemClick(String user) {
        dismiss();
        if (mOnPickUserUpListener != null) {
            mOnPickUserUpListener.onPickUpUser(user);
        }
    }

    public interface OnPickUserUpListener {
        void onPickUpUser(String user);
    }
}