package com.example.android.scopeddirectoryaccess;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 访问内外部存储空间的例子
 */
public class ScopedDirectoryAccessFragment extends Fragment {

    private static final String DIRECTORY_ENTRIES_KEY = "directory_entries";
    private static final String SELECTED_DIRECTORY_KEY = "selected_directory";
    private static final int OPEN_DIRECTORY_REQUEST_CODE = 1;

    private static final String[] DIRECTORY_SELECTION = new String[]{
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    };

    private Activity mActivity;
    private StorageManager mStorageManager;
    private TextView mCurrentDirectoryTextView;
    private TextView mNothingInDirectoryTextView;
    private TextView mPrimaryVolumeNameTextView;
    private Spinner mDirectoriesSpinner;
    private DirectoryEntryAdapter mAdapter;
    private ArrayList<DirectoryEntry> mDirectoryEntries;

    public static ScopedDirectoryAccessFragment newInstance() {
        ScopedDirectoryAccessFragment fragment = new ScopedDirectoryAccessFragment();
        return fragment;
    }

    public ScopedDirectoryAccessFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = getActivity();
        mStorageManager = mActivity.getSystemService(StorageManager.class);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 回调
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // 向用户获取权读取内部存储和外部存储的权限
            getActivity().getContentResolver().takePersistableUriPermission(data.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            updateDirectoryEntries(data.getData());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scoped_directory_access, container, false);
    }

    @Override
    public void onViewCreated(final View rootView, Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        mCurrentDirectoryTextView = (TextView) rootView
                .findViewById(R.id.textview_current_directory);
        mNothingInDirectoryTextView = (TextView) rootView
                .findViewById(R.id.textview_nothing_in_directory);
        mPrimaryVolumeNameTextView = (TextView) rootView
                .findViewById(R.id.textview_primary_volume_name);

        Button openPictureButton = (Button) rootView
                .findViewById(R.id.button_open_directory_primary_volume);
        /* 内部存储空间按钮 */
        openPictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 得到spinner的选择项
                String selected = mDirectoriesSpinner.getSelectedItem().toString();
                // 获取到要访问的目录名称
                String directoryName = getDirectoryName(selected);
                // 获取到外部存储空间的大小
                StorageVolume storageVolume = mStorageManager.getPrimaryStorageVolume();
                // 创建一个访问的意图，得到用户的允许之后，就可以访问了
                Intent intent = storageVolume.createAccessIntent(directoryName);
                // 启动
                startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE);
            }
        });

        //获取外部存储空间
        List<StorageVolume> storageVolumes = mStorageManager.getStorageVolumes();
        LinearLayout containerVolumes = (LinearLayout) mActivity
                .findViewById(R.id.container_volumes);
        //如果内部存储空间（sd卡）存在的话，就加入访问外部存储空间的按钮
        for (final StorageVolume volume : storageVolumes) {
            String volumeDescription = volume.getDescription(mActivity);
            if (volume.isPrimary()) {
                // Primary volume area is already added...
                if (volumeDescription != null) {
                    // 设置外部存储的真实名称，如果可用的话
                    mPrimaryVolumeNameTextView.setText(volumeDescription);
                }
                continue;
            }
            // 加载自定义布局
            LinearLayout volumeArea = (LinearLayout) mActivity.getLayoutInflater()
                    .inflate(R.layout.volume_entry, containerVolumes);
            TextView volumeName = (TextView) volumeArea.findViewById(R.id.textview_volume_name);
            volumeName.setText(volumeDescription);
            Button button = (Button) volumeArea.findViewById(R.id.button_open_directory);
            // 设置点击事件
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String selected = mDirectoriesSpinner.getSelectedItem().toString();
                    String directoryName = getDirectoryName(selected);
                    Intent intent = volume.createAccessIntent(directoryName);
                    startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE);
                }
            });
        }

        RecyclerView recyclerView = (RecyclerView) rootView
                .findViewById(R.id.recyclerview_directory_entries);
        // 这里做一个判断，是否之前有保存此fragment的状态
        if (savedInstanceState != null) {
            mDirectoryEntries = savedInstanceState.getParcelableArrayList(DIRECTORY_ENTRIES_KEY);
            mCurrentDirectoryTextView.setText(savedInstanceState.getString(SELECTED_DIRECTORY_KEY));
            mAdapter = new DirectoryEntryAdapter(mDirectoryEntries);
            if (mAdapter.getItemCount() == 0) {
                mNothingInDirectoryTextView.setVisibility(View.VISIBLE);
            }
        } else {
            mDirectoryEntries = new ArrayList<>();
            mAdapter = new DirectoryEntryAdapter();
        }
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        // 给spinner设置值
        mDirectoriesSpinner = (Spinner) rootView.findViewById(R.id.spinner_directories);
        ArrayAdapter<CharSequence> directoriesAdapter = ArrayAdapter
                .createFromResource(getActivity(),
                        R.array.directories, android.R.layout.simple_spinner_item);
        directoriesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDirectoriesSpinner.setAdapter(directoriesAdapter);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 当fragment被系统意外杀死是，会调用此方法
        // 如果有不明白的，可以看 这个文章：
        // http://blog.csdn.net/qq_16628781/article/details/60877412
        outState.putString(SELECTED_DIRECTORY_KEY, mCurrentDirectoryTextView.getText().toString());
        outState.putParcelableArrayList(DIRECTORY_ENTRIES_KEY, mDirectoryEntries);
    }

    private void updateDirectoryEntries(Uri uri) {
        mDirectoryEntries.clear();

        // 这里涉及到内容提供者，可以搜索内容提供者的相关知识
        // 也可以看这篇文章的解释：http://blog.csdn.net/qq_16628781/article/details/61195621
        // 获取内容获得者
        ContentResolver contentResolver = getActivity().getContentResolver();

        // 根据URI和id，建立一个URI代表目标去访问内容提供者
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));
        // 访问URI的子目录
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));

        // 查询URI提供者
        try (Cursor docCursor = contentResolver
                .query(docUri, DIRECTORY_SELECTION, null, null, null)) {
            while (docCursor != null && docCursor.moveToNext()) {
                mCurrentDirectoryTextView.setText(docCursor.getString(docCursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME)));
            }
        }

        // 查询子目录
        try (Cursor childCursor = contentResolver
                .query(childrenUri, DIRECTORY_SELECTION, null, null, null)) {
            while (childCursor != null && childCursor.moveToNext()) {
                // 获得子目录下的所有文件夹，最后在recycleview里头展示出来
                DirectoryEntry entry = new DirectoryEntry();
                entry.fileName = childCursor.getString(childCursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                entry.mimeType = childCursor.getString(childCursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_MIME_TYPE));
                mDirectoryEntries.add(entry);
            }

            if (mDirectoryEntries.isEmpty()) {
                mNothingInDirectoryTextView.setVisibility(View.VISIBLE);
            } else {
                mNothingInDirectoryTextView.setVisibility(View.GONE);
            }
            mAdapter.setDirectoryEntries(mDirectoryEntries);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 获取目录的名称
     *
     * @param name name
     * @return name，比如有dcim图片目录，下载目录，音乐等等文件类型
     */
    private String getDirectoryName(String name) {
        switch (name) {
            case "ALARMS":
                return Environment.DIRECTORY_ALARMS;
            case "DCIM":
                return Environment.DIRECTORY_DCIM;
            case "DOCUMENTS":
                return Environment.DIRECTORY_DOCUMENTS;
            case "DOWNLOADS":
                return Environment.DIRECTORY_DOWNLOADS;
            case "MOVIES":
                return Environment.DIRECTORY_MOVIES;
            case "MUSIC":
                return Environment.DIRECTORY_MUSIC;
            case "NOTIFICATIONS":
                return Environment.DIRECTORY_NOTIFICATIONS;
            case "PICTURES":
                return Environment.DIRECTORY_PICTURES;
            case "PODCASTS":
                return Environment.DIRECTORY_PODCASTS;
            case "RINGTONES":
                return Environment.DIRECTORY_RINGTONES;
            default:
                throw new IllegalArgumentException("Invalid directory representation: " + name);
        }
    }
}
