package com.zego.expressDemo.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zego.expressDemo.R;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    private List<String> mUserList;

    private OnUserItemClickListener mOnUserItemClickListener;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_user_layout, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        String userID = mUserList.get(position);
        viewHolder.mTvUserName.setText(TextUtils.isEmpty(userID) ? "我" : userID);
        viewHolder.itemView.setTag(userID);
    }

    @Override
    public int getItemCount() {
        return mUserList == null ? 0 : mUserList.size();
    }

    public void setUserList(List<String> userList) {
        mUserList = userList;
        notifyDataSetChanged();
    }

    public void setOnUserItemClickListener(OnUserItemClickListener onUserItemClickListener) {
        this.mOnUserItemClickListener = onUserItemClickListener;
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView mTvUserName;

        ViewHolder(View view) {
            super(view);

            mTvUserName = view.findViewById(R.id.tv_user_name);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (mOnUserItemClickListener != null) {
                mOnUserItemClickListener.OnUserItemClick((String) v.getTag());
            }
        }
    }

    public interface OnUserItemClickListener {
        void OnUserItemClick(String user);
    }
}
