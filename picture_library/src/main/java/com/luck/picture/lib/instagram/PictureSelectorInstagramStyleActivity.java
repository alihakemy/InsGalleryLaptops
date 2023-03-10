package com.luck.picture.lib.instagram;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.luck.picture.lib.PictureBaseActivity;
import com.luck.picture.lib.PictureCustomCameraActivity;
import com.luck.picture.lib.PictureMediaScannerConnection;
import com.luck.picture.lib.PictureSelector;
import com.luck.picture.lib.R;
import com.luck.picture.lib.camera.listener.CameraListener;
import com.luck.picture.lib.config.PictureConfig;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.config.PictureSelectionConfig;
import com.luck.picture.lib.dialog.PhotoItemSelectedDialog;
import com.luck.picture.lib.dialog.PictureCustomDialog;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.entity.LocalMediaFolder;
import com.luck.picture.lib.instagram.cache.LruCache;
import com.luck.picture.lib.instagram.process.InstagramMediaProcessActivity;
import com.luck.picture.lib.listener.OnAlbumItemClickListener;
import com.luck.picture.lib.listener.OnItemClickListener;
import com.luck.picture.lib.model.LocalMediaLoader;
import com.luck.picture.lib.permissions.PermissionChecker;
import com.luck.picture.lib.style.PictureWindowAnimationStyle;
import com.luck.picture.lib.thread.PictureThreadUtils;
import com.luck.picture.lib.tools.BitmapUtils;
import com.luck.picture.lib.tools.DateUtils;
import com.luck.picture.lib.tools.DoubleUtils;
import com.luck.picture.lib.tools.MediaUtils;
import com.luck.picture.lib.tools.PictureFileUtils;
import com.luck.picture.lib.tools.SPUtils;
import com.luck.picture.lib.tools.ScreenUtils;
import com.luck.picture.lib.tools.SdkVersionUtils;
import com.luck.picture.lib.tools.StringUtils;
import com.luck.picture.lib.tools.ToastUtils;
import com.luck.picture.lib.tools.ValueOf;
import com.luck.picture.lib.widget.FolderPopWindow;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.callback.BitmapLoadCallback;
import com.yalantis.ucrop.model.CutInfo;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.task.BitmapCropTask;
import com.yalantis.ucrop.util.BitmapLoadUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

/**
 * @author???luck
 * @data???2018/1/27 19:12
 * @??????: Media ????????????
 */
public class PictureSelectorInstagramStyleActivity extends PictureBaseActivity implements View.OnClickListener,
        OnAlbumItemClickListener,
        InstagramImageGridAdapter.OnPhotoSelectChangedListener, OnItemClickListener {
    private static final String RECORD_AUDIO_PERMISSION = "RECORD_AUDIO_PERMISSION";
    protected ImageView mIvPictureLeftBack;
    protected ImageView mIvArrow;
    protected View titleViewBg;
    protected TextView mTvPictureTitle, mTvPictureRight, mTvEmpty, mTvPlayPause, mTvStop, mTvQuit,
            mTvMusicStatus, mTvMusicTotal, mTvMusicTime;
    protected RecyclerView mPictureRecycler;
    protected InstagramImageGridAdapter mAdapter;
    protected List<LocalMedia> images = new ArrayList<>();
    protected List<LocalMediaFolder> foldersList = new ArrayList<>();
    protected FolderPopWindow folderWindow;
    protected MediaPlayer mediaPlayer;
    protected SeekBar musicSeekBar;
    protected boolean isPlayAudio = false;
    protected PictureCustomDialog audioDialog;
    protected int oldCurrentListSize;
    protected boolean isFirstEnterActivity = false;
    protected boolean isEnterSetting;
    protected InstagramGallery mInstagramGallery;
    private InstagramPreviewContainer mPreviewContainer;
    private int mPreviewPosition = -1;
    private InstagramViewPager mInstagramViewPager;
    private boolean isRunningBind;
    private String mTitle;
    private List<Page> mList;
    private long intervalClickTime;
    private LruCache<LocalMedia, AsyncTask> mLruCache;
    private boolean isCroppingImage;
    private int mFolderPosition;
    private int mPreviousFolderPosition;
    private boolean isChangeFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            oldCurrentListSize = savedInstanceState.getInt(PictureConfig.EXTRA_OLD_CURRENT_LIST_SIZE, 0);
            // ???????????????????????????activity?????????????????????????????????????????????
            selectionMedias = PictureSelector.obtainSelectorList(savedInstanceState);
            if (mAdapter != null) {
                mAdapter.bindSelectImages(selectionMedias);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mInstagramViewPager.getSelectedPosition() == 0 && mPreviewContainer != null) {
            mPreviewContainer.onResume();
        }
        if (mInstagramViewPager != null) {
            mInstagramViewPager.onResume();
        }
        // ???????????????????????????????????????????????????????????????????????????????????????
        if (isEnterSetting) {
            if (PermissionChecker
                    .checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    PermissionChecker
                            .checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (mAdapter.isDataEmpty()) {
                    readLocalMedia();
                }
            } else {
                showPermissionsDialog(false, getString(R.string.picture_jurisdiction));
            }
            isEnterSetting = false;
        }
        if (mInstagramViewPager != null) {
            if (mInstagramViewPager.getSelectedPosition() == 1 || mInstagramViewPager.getSelectedPosition() == 2) {
                if (PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                    initCamera();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPreviewContainer != null) {
            mPreviewContainer.onPause();
        }
        if (mInstagramViewPager != null) {
            mInstagramViewPager.onPause();
        }
    }

    @Override
    public int getResourceId() {
        return R.layout.picture_instagram_style_selector;
    }

    @Override
    protected void initWidgets() {
        super.initWidgets();
        container = findViewById(R.id.container);
        titleViewBg = findViewById(R.id.titleViewBg);
        mIvPictureLeftBack = findViewById(R.id.pictureLeftBack);
        mTvPictureTitle = findViewById(R.id.picture_title);
        mTvPictureRight = findViewById(R.id.picture_right);
        mIvArrow = findViewById(R.id.ivArrow);

        config.isCamera = false;
        config.selectionMode = PictureConfig.SINGLE;
        config.isSingleDirectReturn = true;
        config.isWithVideoImage = false;
        config.maxVideoSelectNum = 1;
        config.aspect_ratio_x = 1;
        config.aspect_ratio_y = 1;
        config.enableCrop = true;
//        config.recordVideoMinSecond = 3;

        mPictureRecycler = new GalleryViewImpl(getContext());
        mPreviewContainer = new InstagramPreviewContainer(getContext(), config);
        mInstagramGallery = new InstagramGallery(getContext(), mPreviewContainer, mPictureRecycler);
        mInstagramGallery.setPreviewBottomMargin(ScreenUtils.dip2px(getContext(), 2));

        mPreviewContainer.setListener(new InstagramPreviewContainer.onSelectionModeChangedListener() {
            @Override
            public void onSelectionModeChange(boolean isMulti) {
                if (isMulti) {
                    config.selectionMode = PictureConfig.MULTIPLE;
                    config.isSingleDirectReturn = false;
                    if (mInstagramViewPager != null) {
                        mInstagramGallery.setInitGalleryHeight();
                        mInstagramViewPager.setScrollEnable(false);
                        mInstagramViewPager.displayTabLayout(false);
                    }
                    if (mLruCache == null) {
                        mLruCache = new LruCache<>(20);
                    }
                    bindPreviewPosition();
                } else {
                    config.selectionMode = PictureConfig.SINGLE;
                    config.isSingleDirectReturn = true;
                    if (mInstagramViewPager != null) {
                        mInstagramGallery.setInitGalleryHeight();
                        mInstagramViewPager.setScrollEnable(true);
                        mInstagramViewPager.displayTabLayout(true);
                    }
                }
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onRatioChange(boolean isOneToOne) {
                if (isOneToOne) {
                    config.aspect_ratio_x = 0;
                    config.aspect_ratio_y = 0;
                } else {
                    config.aspect_ratio_x = 1;
                    config.aspect_ratio_y = 1;
                }
            }
        });

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.BELOW, R.id.titleViewBg);

        mList = new ArrayList<>();
        mList.add(new PageGallery(mInstagramGallery));
        PagePhoto pagePhoto = new PagePhoto(this, config);
        mList.add(pagePhoto);
        mList.add(new PageVideo(pagePhoto));
        mInstagramViewPager = new InstagramViewPager(getContext(), mList, config);
        ((RelativeLayout) container).addView(mInstagramViewPager, params);

        pagePhoto.setCameraListener(new CameraListener() {
            @Override
            public void onPictureSuccess(@NonNull File file) {
                Intent intent = new Intent();
                intent.putExtra(PictureConfig.EXTRA_MEDIA_PATH, file.getAbsolutePath());
                requestCamera(intent);
            }

            @Override
            public void onRecordSuccess(@NonNull File file) {
                Intent intent = new Intent();
                intent.putExtra(PictureConfig.EXTRA_MEDIA_PATH, file.getAbsolutePath());
                requestCamera(intent);
            }

            @Override
            public void onError(int videoCaptureError, String message, Throwable cause) {
                if (videoCaptureError == -1) {
                    onTakePhoto();
                } else {
                    ToastUtils.s(getContext(), message);
                }
            }
        });

        mInstagramViewPager.setSkipRange(1);
        mInstagramViewPager.setOnPageChangeListener(new OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (position == 0) {
                    if (positionOffset >= 0.5f) {
                        mPreviewContainer.pauseVideo(true);
                    } else {
                        mPreviewContainer.pauseVideo(false);
                    }
                }
                if (isRunningBind) {
                    mHandler.removeCallbacks(mBindRunnable);
                    isRunningBind = false;
                }
                if (position == 1) {
                    ((PagePhoto) mList.get(1)).setCaptureButtonTranslationX(-positionOffsetPixels);
                } else if (position == 2 && positionOffsetPixels == 0) {
                    ((PagePhoto) mList.get(1)).setCaptureButtonTranslationX(-mInstagramViewPager.getMeasuredWidth());
                }
            }

            @Override
            public void onPageSelected(int position) {
                if (position == 1 || position == 2) {
                    onTakePhoto();
                }
                changeTabState(position);
                if (position == 1) {
                    ((PagePhoto) mList.get(1)).setCameraState(InstagramCameraView.STATE_CAPTURE);
                } else if (position == 2) {
                    ((PagePhoto) mList.get(1)).setCameraState(InstagramCameraView.STATE_RECORDER);
                }
            }
        });

        mTvEmpty = mInstagramGallery.getEmptyView();
        isNumComplete(numComplete);

        if (config.instagramSelectionConfig.getCurrentTheme() == InsGallery.THEME_STYLE_DEFAULT) {
            mIvPictureLeftBack.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.picture_color_black), PorterDuff.Mode.MULTIPLY));
            mIvArrow.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.picture_color_black), PorterDuff.Mode.MULTIPLY));
        }

        if (config.isAutomaticTitleRecyclerTop) {
            titleViewBg.setOnClickListener(this);
        }
        mIvPictureLeftBack.setOnClickListener(this);
        mTvPictureRight.setOnClickListener(this);
        mTvPictureTitle.setOnClickListener(this);
        mIvArrow.setOnClickListener(this);
        mTitle = config.chooseMode == PictureMimeType.ofAudio() ?
                getString(R.string.picture_all_audio) : getString(R.string.picture_camera_roll);
        mTvPictureTitle.setText(mTitle);
        folderWindow = new FolderPopWindow(this, config);
        folderWindow.setArrowImageView(mIvArrow);
        folderWindow.setOnAlbumItemClickListener(this);
        mPictureRecycler.setHasFixedSize(true);
        mPictureRecycler.addItemDecoration(new SpacingItemDecoration(config.imageSpanCount,
                ScreenUtils.dip2px(this, 2), false));
        mPictureRecycler.setLayoutManager(new GridLayoutManager(getContext(), config.imageSpanCount));
        // ???????????? notifyItemChanged ????????????,??????????????????
        ((SimpleItemAnimator) mPictureRecycler.getItemAnimator())
                .setSupportsChangeAnimations(false);
        if (config.isFallbackVersion2
                || Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            loadAllMediaData();
        }
        mTvEmpty.setText(config.chooseMode == PictureMimeType.ofAudio() ?
                getString(R.string.picture_audio_empty)
                : getString(R.string.picture_empty));
        StringUtils.tempTextFont(mTvEmpty, config.chooseMode);
        mAdapter = new InstagramImageGridAdapter(getContext(), config);
        mAdapter.setOnPhotoSelectChangedListener(this);
        mPictureRecycler.setAdapter(mAdapter);
    }

    private void bindPreviewPosition() {
        if (mAdapter != null) {
            List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
            int size = selectedImages.size();
            String mimeType = size > 0 ? selectedImages.get(0).getMimeType() : "";
            LocalMedia previewMedia = mAdapter.getImages().get(mPreviewPosition);

            if (selectedImages.contains(previewMedia) || containsMedia(selectedImages, previewMedia)) {
                return;
            }

            if (!TextUtils.isEmpty(mimeType)) {
                boolean mimeTypeSame = PictureMimeType.isMimeTypeSame(mimeType, previewMedia.getMimeType());
                if (!mimeTypeSame) {
                    ToastUtils.s(getContext(), getString(R.string.picture_rule));
                    return;
                }
            }

            if (PictureMimeType.isHasVideo(mimeType) && config.maxVideoSelectNum > 0) {
                if (size >= config.maxVideoSelectNum) {
                    // ???????????????????????????
                    ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), mimeType, config.maxVideoSelectNum));
                    return;
                }
                if (config.videoMinSecond > 0 && previewMedia.getDuration() < config.videoMinSecond) {
                    // ?????????????????????????????????
                    ToastUtils.s(getContext(),
                            getContext().getString(R.string.picture_choose_min_seconds, config.videoMinSecond / 1000));
                    return;
                }

                if (config.videoMaxSecond > 0 && previewMedia.getDuration() > config.videoMaxSecond) {
                    // ????????????????????????????????????
                    ToastUtils.s(getContext(),
                            getContext().getString(R.string.picture_choose_max_seconds, config.videoMaxSecond / 1000));
                    return;
                }
            } else {
                if (size >= config.maxSelectNum) {
                    ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), mimeType, config.maxSelectNum));
                    return;
                }
                if (PictureMimeType.isHasVideo(previewMedia.getMimeType())) {
                    if (config.videoMinSecond > 0 && previewMedia.getDuration() < config.videoMinSecond) {
                        // ?????????????????????????????????
                        ToastUtils.s(getContext(),
                                getContext().getString(R.string.picture_choose_min_seconds, config.videoMinSecond / 1000));
                        return;
                    }

                    if (config.videoMaxSecond > 0 && previewMedia.getDuration() > config.videoMaxSecond) {
                        // ????????????????????????????????????
                        ToastUtils.s(getContext(),
                                getContext().getString(R.string.picture_choose_max_seconds, config.videoMaxSecond / 1000));
                        return;
                    }
                }
            }

            selectedImages.add(previewMedia);
            mAdapter.bindSelectImages(selectedImages);
        }
    }

    public boolean containsMedia(List<LocalMedia> selectedImages, LocalMedia media) {
        if (selectedImages != null && media != null) {
            for (LocalMedia selectedImage : selectedImages) {
                if (selectedImage.getPath().equals(media.getPath()) || selectedImage.getId() == media.getId()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void changeTabState(int position) {
        String title;
        boolean enable;
        if (position == 1) {
            title = getString(R.string.photo);
            enable = false;
        } else if (position == 2) {
            title = getString(R.string.video);
            enable = false;
        } else {
            title = mTitle;
            enable = true;
        }
        if (enable) {
            mIvArrow.setVisibility(View.VISIBLE);
            mTvPictureRight.setVisibility(View.VISIBLE);
        } else {
            mIvArrow.setVisibility(View.INVISIBLE);
            mTvPictureRight.setVisibility(View.INVISIBLE);
        }
        mTvPictureTitle.setEnabled(enable);
        mTvPictureTitle.setText(title);
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            if (!config.isFallbackVersion2) {
                if (!isFirstEnterActivity) {
                    loadAllMediaData();
                    isFirstEnterActivity = true;
                }
            }
        }
    }

    /**
     * ????????????
     */
    private void loadAllMediaData() {
        if (PermissionChecker
                .checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                PermissionChecker
                        .checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            readLocalMedia();
        } else {
            PermissionChecker.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, PictureConfig.APPLY_STORAGE_PERMISSIONS_CODE);
        }
    }

    /**
     * ????????????????????????
     */
    @Override
    public void initPictureSelectorStyle() {
        if (config.style != null) {
            if (config.style.pictureTitleDownResId != 0) {
                Drawable drawable = ContextCompat.getDrawable(this, config.style.pictureTitleDownResId);
                mIvArrow.setImageDrawable(drawable);
            }
            if (config.style.pictureTitleTextColor != 0) {
                mTvPictureTitle.setTextColor(config.style.pictureTitleTextColor);
            }
            if (config.style.pictureTitleTextSize != 0) {
                mTvPictureTitle.setTextSize(config.style.pictureTitleTextSize);
            }

            if (config.style.pictureRightDefaultTextColor != 0) {
                mTvPictureRight.setTextColor(config.style.pictureRightDefaultTextColor);
            } else {
                if (config.style.pictureCancelTextColor != 0) {
                    mTvPictureRight.setTextColor(config.style.pictureCancelTextColor);
                }
            }

            if (config.style.pictureRightTextSize != 0) {
                mTvPictureRight.setTextSize(config.style.pictureRightTextSize);
            }

            if (config.style.pictureLeftBackIcon != 0) {
                mIvPictureLeftBack.setImageResource(config.style.pictureLeftBackIcon);
            }
            if (config.style.pictureContainerBackgroundColor != 0) {
                container.setBackgroundColor(config.style.pictureContainerBackgroundColor);
            }
            if (!TextUtils.isEmpty(config.style.pictureRightDefaultText)) {
                mTvPictureRight.setText(config.style.pictureRightDefaultText);
            }
        } else {
            if (config.downResId != 0) {
                Drawable drawable = ContextCompat.getDrawable(this, config.downResId);
                mIvArrow.setImageDrawable(drawable);
            }
        }
        titleViewBg.setBackgroundColor(colorPrimary);

        mAdapter.bindSelectImages(selectionMedias);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (images != null) {
            // ??????????????????????????????????????????
            outState.putInt(PictureConfig.EXTRA_OLD_CURRENT_LIST_SIZE, images.size());
        }
        if (mAdapter != null && mAdapter.getSelectedImages() != null) {
            List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
            PictureSelector.saveSelectorList(outState, selectedImages);
        }
    }

    /**
     * none number style
     */
    private void isNumComplete(boolean numComplete) {
        if (numComplete) {
            initCompleteText(0);
        }
    }

    /**
     * init ????????????
     */
    @Override
    protected void initCompleteText(int startCount) {

    }


    /**
     * get LocalMedia s
     */
    protected void readLocalMedia() {
        showPleaseDialog();
        PictureThreadUtils.executeByCached(new PictureThreadUtils.SimpleTask<List<LocalMediaFolder>>() {

            @Override
            public List<LocalMediaFolder> doInBackground() {
                return new LocalMediaLoader(getContext(), config).loadAllMedia();
            }

            @Override
            public void onSuccess(List<LocalMediaFolder> folders) {
                dismissDialog();
                PictureThreadUtils.cancel(PictureThreadUtils.getCachedPool());
                if (folders != null) {
                    if (folders.size() > 0) {
                        foldersList = folders;
                        LocalMediaFolder folder = folders.get(0);
                        folder.setChecked(true);
                        List<LocalMedia> result = folder.getData();
                        if (images == null) {
                            images = new ArrayList<>();
                        }
                        // ??????????????????????????????????????????????????????????????????????????????
                        // ??????onActivityResult????????????????????????????????????
                        // ????????????????????????????????????????????????adapter?????????????????????????????????????????????????????????
                        int currentSize = images.size();
                        int resultSize = result.size();
                        oldCurrentListSize = oldCurrentListSize + currentSize;
                        if (resultSize >= currentSize) {
                            if (currentSize > 0 && currentSize < resultSize && oldCurrentListSize != resultSize) {
                                // ???????????????????????????????????????Activity?????????????????????????????????
                                images.addAll(result);
                                // ????????????????????????
                                LocalMedia media = images.get(0);
                                folder.setFirstImagePath(media.getPath());
                                folder.getData().add(0, media);
                                folder.setCheckedNum(1);
                                folder.setImageNum(folder.getImageNum() + 1);
                                // ????????????????????????
                                updateMediaFolder(foldersList, media);
                            } else {
                                // ???????????????
                                images = result;
                            }
                            folderWindow.bindFolder(folders);
                        }
                    }
                    if (mAdapter != null) {
                        mAdapter.bindImagesData(images);
                        boolean isEmpty = images.size() > 0;
                        if (!isEmpty) {
                            mTvEmpty.setText(getString(R.string.picture_empty));
                            mTvEmpty.setCompoundDrawablesRelativeWithIntrinsicBounds
                                    (0, R.drawable.picture_icon_no_data, 0, 0);
                        } else {
                            //???????????????????????????
                            startPreview(images, 0);
                        }
                        mTvEmpty.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);
                        mInstagramGallery.setViewVisibility(isEmpty ? View.VISIBLE : View.INVISIBLE);
                        boolean enabled = isEmpty || config.returnEmpty;
                        mTvPictureRight.setEnabled(enabled);
                        mTvPictureRight.setTextColor(enabled ? config.style.pictureRightDefaultTextColor : ContextCompat.getColor(getContext(), R.color.picture_color_9B9B9D));
                    }
                } else {
                    mTvEmpty.setCompoundDrawablesRelativeWithIntrinsicBounds
                            (0, R.drawable.picture_icon_data_error, 0, 0);
                    mTvEmpty.setText(getString(R.string.picture_data_exception));
                    mTvEmpty.setVisibility(images.size() > 0 ? View.INVISIBLE : View.VISIBLE);
                    mInstagramGallery.setViewVisibility(images.size() > 0 ? View.VISIBLE : View.INVISIBLE);
                    boolean enabled = images.size() > 0 || config.returnEmpty;
                    mTvPictureRight.setEnabled(enabled);
                    mTvPictureRight.setTextColor(enabled ? config.style.pictureRightDefaultTextColor : ContextCompat.getColor(getContext(), R.color.picture_color_9B9B9D));
                }
            }
        });
    }

    /**
     * open camera
     */
    public void startCamera() {
        // ?????????????????????????????????????????????
        if (!DoubleUtils.isFastDoubleClick()) {
            if (PictureSelectionConfig.onCustomCameraInterfaceListener != null) {
                // ?????????????????????????????????
                if (config.chooseMode == PictureConfig.TYPE_ALL) {
                    // ?????????????????????????????????????????????????????? (????????????????????????new???PopupWindow??????)
                    PhotoItemSelectedDialog selectedDialog = PhotoItemSelectedDialog.newInstance();
                    selectedDialog.setOnItemClickListener(this);
                    selectedDialog.show(getSupportFragmentManager(), "PhotoItemSelectedDialog");
                } else {
                    PictureSelectionConfig.onCustomCameraInterfaceListener.onCameraClick(getContext(), config, config.chooseMode);
                }
                return;
            }
            if (config.isUseCustomCamera) {
                startCustomCamera();
                return;
            }
            switch (config.chooseMode) {
                case PictureConfig.TYPE_ALL:
                    // ?????????????????????????????????????????????????????? (????????????????????????new???PopupWindow??????)
                    PhotoItemSelectedDialog selectedDialog = PhotoItemSelectedDialog.newInstance();
                    selectedDialog.setOnItemClickListener(this);
                    selectedDialog.show(getSupportFragmentManager(), "PhotoItemSelectedDialog");
                    break;
                case PictureConfig.TYPE_IMAGE:
                    // ??????
                    startOpenCamera();
                    break;
                case PictureConfig.TYPE_VIDEO:
                    // ?????????
                    startOpenCameraVideo();
                    break;
                case PictureConfig.TYPE_AUDIO:
                    // ??????
                    startOpenCameraAudio();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * ?????????????????????
     */
    private void startCustomCamera() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            Intent intent = new Intent(this, PictureCustomCameraActivity.class);
            startActivityForResult(intent, PictureConfig.REQUEST_CAMERA);
            PictureWindowAnimationStyle windowAnimationStyle = config.windowAnimationStyle;
            overridePendingTransition(windowAnimationStyle != null &&
                    windowAnimationStyle.activityEnterAnimation != 0 ?
                    windowAnimationStyle.activityEnterAnimation :
                    R.anim.picture_anim_enter, R.anim.picture_anim_fade_in);
        } else {
            PermissionChecker
                    .requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO}, PictureConfig.APPLY_RECORD_AUDIO_PERMISSIONS_CODE);
        }
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.pictureLeftBack) {
            if (folderWindow != null && folderWindow.isShowing()) {
                folderWindow.dismiss();
            } else {
                onBackPressed();
            }
        } else if (id == R.id.picture_title || id == R.id.ivArrow) {
            if (folderWindow.isShowing()) {
                folderWindow.dismiss();
            } else {
                if (images != null && images.size() > 0) {
                    folderWindow.showAsDropDown(titleViewBg);
                    if (!config.isSingleDirectReturn) {
                        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                        folderWindow.updateFolderCheckStatus(selectedImages);
                    }
                }
            }
        } else if (id == R.id.picture_right) {
            onComplete();
        } else if (id == R.id.titleViewBg) {
            if (mInstagramViewPager.getSelectedPosition() == 0 && config.isAutomaticTitleRecyclerTop) {
                int intervalTime = 500;
                if (SystemClock.uptimeMillis() - intervalClickTime < intervalTime) {
                    if (mAdapter.getItemCount() > 0) {
                        mPictureRecycler.smoothScrollToPosition(0);
                    }
                } else {
                    intervalClickTime = SystemClock.uptimeMillis();
                }
            }
        }
    }

    /**
     * ????????????
     */
    private void onComplete() {
        List<LocalMedia> result = mAdapter.getSelectedImages();
        if (config.selectionMode == PictureConfig.SINGLE) {
            if (result.size() > 0) {
                result.clear();
            }
            if (mAdapter.getImages().size() > 0) {
                result.add(mAdapter.getImages().get(mPreviewPosition));
            }
        }
        int size = result.size();
        LocalMedia image = result.size() > 0 ? result.get(0) : null;
        String mimeType = image != null ? image.getMimeType() : "";
        // ?????????????????????????????????????????????????????????????????????
        boolean eqImg = PictureMimeType.isHasImage(mimeType);
        if (config.isWithVideoImage) {
            // ????????????
            int videoSize = 0;
            int imageSize = 0;
            for (int i = 0; i < size; i++) {
                LocalMedia media = result.get(i);
                if (PictureMimeType.isHasVideo(media.getMimeType())) {
                    videoSize++;
                } else {
                    imageSize++;
                }
            }
            if (config.selectionMode == PictureConfig.MULTIPLE) {
                if (config.minSelectNum > 0) {
                    if (imageSize < config.minSelectNum) {
                        ToastUtils.s(getContext(), getString(R.string.picture_min_img_num, config.minSelectNum));
                        return;
                    }
                }
                if (config.minVideoSelectNum > 0) {
                    if (videoSize < config.minVideoSelectNum) {
                        ToastUtils.s(getContext(), getString(R.string.picture_min_video_num, config.minVideoSelectNum));
                        return;
                    }
                }
            }
        } else {
            if (config.selectionMode == PictureConfig.MULTIPLE) {
                if (PictureMimeType.isHasImage(mimeType) && config.minSelectNum > 0 && size < config.minSelectNum) {
                    String str = getString(R.string.picture_min_img_num, config.minSelectNum);
                    ToastUtils.s(getContext(), str);
                    return;
                }
                if (PictureMimeType.isHasVideo(mimeType) && config.minVideoSelectNum > 0 && size < config.minVideoSelectNum) {
                    String str = getString(R.string.picture_min_video_num, config.minVideoSelectNum);
                    ToastUtils.s(getContext(), str);
                    return;
                }
            }
        }

        // ??????????????????????????????????????????????????????????????????
        if (size == 0) {
            if (config.returnEmpty) {
                if (PictureSelectionConfig.listener != null) {
                    PictureSelectionConfig.listener.onResult(result);
                } else {
                    Intent intent = PictureSelector.putIntentResult(result);
                    setResult(RESULT_OK, intent);
                }
                closeActivity();
                return;
            }

            if (config.minSelectNum > 0 && size < config.minSelectNum) {
                String str = getString(R.string.picture_min_img_num, config.minSelectNum);
                ToastUtils.s(getContext(), str);
                return;
            }
            if (config.minVideoSelectNum > 0 && size < config.minVideoSelectNum) {
                String str = getString(R.string.picture_min_video_num, config.minVideoSelectNum);
                ToastUtils.s(getContext(), str);
                return;
            }
        }

        if (config.isCheckOriginalImage) {
            onResult(result);
            return;
        }
        if (config.chooseMode == PictureMimeType.ofAll() && config.isWithVideoImage) {
            // ???????????????????????????
            bothMimeTypeWith(eqImg, result);
        } else {
            // ????????????
            separateMimeTypeWith(eqImg, result);
        }
    }


    /**
     * ?????????????????????????????????
     *
     * @param eqImg
     * @param images
     */
    private void bothMimeTypeWith(boolean eqImg, List<LocalMedia> images) {
        LocalMedia image = images.size() > 0 ? images.get(0) : null;
        if (config.enableCrop) {
            if (config.selectionMode == PictureConfig.SINGLE && eqImg) {
                config.originalPath = image.getPath();
                startCrop(config.originalPath, image.getMimeType());
            } else {
                // ????????????????????????????????????????????????????????????
                ArrayList<CutInfo> cuts = new ArrayList<>();
                int count = images.size();
                int imageNum = 0;
                for (int i = 0; i < count; i++) {
                    LocalMedia media = images.get(i);
                    if (media == null
                            || TextUtils.isEmpty(media.getPath())) {
                        continue;
                    }
                    boolean isHasImage = PictureMimeType.isHasImage(media.getMimeType());
                    if (isHasImage) {
                        imageNum++;
                    }
                    CutInfo cutInfo = new CutInfo();
                    cutInfo.setId(media.getId());
                    cutInfo.setPath(media.getPath());
                    cutInfo.setImageWidth(media.getWidth());
                    cutInfo.setImageHeight(media.getHeight());
                    cutInfo.setMimeType(media.getMimeType());
                    cutInfo.setDuration(media.getDuration());
                    cutInfo.setRealPath(media.getRealPath());
                    cuts.add(cutInfo);
                }
                if (imageNum <= 0) {
                    // ????????????
                    onResult(images);
                } else {
                    // ?????????????????????
                    startCrop(cuts);
                }
            }
        } else if (config.isCompress) {
            int size = images.size();
            int imageNum = 0;
            for (int i = 0; i < size; i++) {
                LocalMedia media = images.get(i);
                boolean isHasImage = PictureMimeType.isHasImage(media.getMimeType());
                if (isHasImage) {
                    imageNum++;
                    break;
                }
            }
            if (imageNum <= 0) {
                // ?????????????????????
                onResult(images);
            } else {
                // ???????????????
                compressImage(images);
            }
        } else {
            onResult(images);
        }
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param eqImg
     * @param images
     */
    private void separateMimeTypeWith(boolean eqImg, List<LocalMedia> images) {
        LocalMedia image = images.size() > 0 ? images.get(0) : null;
        String mimeType = image != null ? image.getMimeType() : "";
        if (config.enableCrop && eqImg) {
            if (config.selectionMode == PictureConfig.SINGLE) {
                if (mPreviewContainer != null) {
                    mPreviewContainer.cropAndSaveImage(this);
                }
            } else {
                // ????????????????????????????????????????????????????????????
//                ArrayList<CutInfo> cuts = new ArrayList<>();
//                int count = images.size();
//                for (int i = 0; i < count; i++) {
//                    LocalMedia media = images.get(i);
//                    if (media == null
//                            || TextUtils.isEmpty(media.getPath())) {
//                        continue;
//                    }
//                    CutInfo cutInfo = new CutInfo();
//                    cutInfo.setId(media.getId());
//                    cutInfo.setPath(media.getPath());
//                    cutInfo.setImageWidth(media.getWidth());
//                    cutInfo.setImageHeight(media.getHeight());
//                    cutInfo.setMimeType(media.getMimeType());
//                    cutInfo.setDuration(media.getDuration());
//                    cutInfo.setRealPath(media.getRealPath());
//                    cuts.add(cutInfo);
//                }
//                startCrop(cuts);
                savePreviousPositionCropInfo(mAdapter.getImages().get(mPreviewPosition));
                startMultiCrop();
            }
        } else if (config.isCompress
                && eqImg) {
            // ??????????????????????????????
            compressImage(images);
        } else if (PictureMimeType.isHasVideo(mimeType)) {
            List<LocalMedia> result = new ArrayList<>();
            result.addAll(images);
            Bundle bundle = null;
            if (mPreviewContainer != null) {
                bundle = new Bundle();
                bundle.putBoolean(InstagramMediaProcessActivity.EXTRA_ASPECT_RATIO, mPreviewContainer.isAspectRatio());
            }
            InstagramMediaProcessActivity.launchActivity(this, config, result, bundle, InstagramMediaProcessActivity.REQUEST_SINGLE_VIDEO_PROCESS);
        } else {
            onResult(images);
        }
    }

//    private void cropVideo(List<LocalMedia> images) {
//        if (images.isEmpty()) {
//            return;
//        }
//        LocalMedia media = images.get(0);
//        File transcodeOutputFile;
//        try {
//            File outputDir = new File(getExternalFilesDir(null), "outputs");
//            //noinspection ResultOfMethodCallIgnored
//            outputDir.mkdir();
//            transcodeOutputFile = File.createTempFile("transcode_" + media.getId(), ".mp4", outputDir);
//        } catch (IOException e) {
//            ToastUtils.s(this, "Failed to create temporary file.");
//            return;
//        }
//
//        showPleaseDialog();
//
//        Resizer resizer = new PassThroughResizer();
//        if (mPreviewContainer != null) {
//            if (mPreviewContainer.isAspectRatio() && mPreviewContainer.getAspectRadio() > 0) {
//                resizer = new AspectRatioResizer(mPreviewContainer.getAspectRadio());
//            } else if (!mPreviewContainer.isAspectRatio()) {
//                resizer = new AspectRatioResizer(1f);
//            }
//        }
//        TrackStrategy videoStrategy = new DefaultVideoStrategy.Builder()
//                .addResizer(resizer)
//                .build();
//
//        DataSink sink = new DefaultDataSink(transcodeOutputFile.getAbsolutePath());
//        TranscoderOptions.Builder builder = Transcoder.into(sink);
//        if (PictureMimeType.isContent(media.getPath())) {
//            builder.addDataSource(getContext(), Uri.parse(media.getPath()));
//        } else {
//            builder.addDataSource(media.getPath());
//        }
//        builder.setListener(new TranscoderListener() {
//            @Override
//            public void onTranscodeProgress(double progress) {
//
//            }
//
//            @Override
//            public void onTranscodeCompleted(int successCode) {
//                if (successCode == Transcoder.SUCCESS_TRANSCODED) {
//                    File file = transcodeOutputFile;
//                    String type = "video/mp4";
//                    Uri uri = FileProvider.getUriForFile(PictureSelectorInstagramStyleActivity.this,
//                            "com.luck.pictureselector.provider", file);
//                    startActivity(new Intent(Intent.ACTION_VIEW)
//                            .setDataAndType(uri, type)
//                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
//                } else if (successCode == Transcoder.SUCCESS_NOT_NEEDED) {
//
//                }
//                dismissDialog();
//            }
//
//            @Override
//            public void onTranscodeCanceled() {
//                dismissDialog();
//            }
//
//            @Override
//            public void onTranscodeFailed(@NonNull Throwable exception) {
//                dismissDialog();
//            }
//        })
//                .setVideoTrackStrategy(videoStrategy)
//                .transcode();
//    }

    /**
     * ????????????
     *
     * @param path
     */
    private void audioDialog(final String path) {
        if (!isFinishing()) {
            audioDialog = new PictureCustomDialog(getContext(), R.layout.picture_audio_dialog);
            audioDialog.getWindow().setWindowAnimations(R.style.Picture_Theme_Dialog_AudioStyle);
            mTvMusicStatus = audioDialog.findViewById(R.id.tv_musicStatus);
            mTvMusicTime = audioDialog.findViewById(R.id.tv_musicTime);
            musicSeekBar = audioDialog.findViewById(R.id.musicSeekBar);
            mTvMusicTotal = audioDialog.findViewById(R.id.tv_musicTotal);
            mTvPlayPause = audioDialog.findViewById(R.id.tv_PlayPause);
            mTvStop = audioDialog.findViewById(R.id.tv_Stop);
            mTvQuit = audioDialog.findViewById(R.id.tv_Quit);
            if (mHandler != null) {
                mHandler.postDelayed(() -> initPlayer(path), 30);
            }
            mTvPlayPause.setOnClickListener(new audioOnClick(path));
            mTvStop.setOnClickListener(new audioOnClick(path));
            mTvQuit.setOnClickListener(new audioOnClick(path));
            musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        mediaPlayer.seekTo(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            audioDialog.setOnDismissListener(dialog -> {
                if (mHandler != null) {
                    mHandler.removeCallbacks(mRunnable);
                }
                new Handler().postDelayed(() -> stop(path), 30);
                try {
                    if (audioDialog != null
                            && audioDialog.isShowing()) {
                        audioDialog.dismiss();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            if (mHandler != null) {
                mHandler.post(mRunnable);
            }
            audioDialog.show();
        }
    }

    //  ?????? Handler ?????? UI ??????????????????
    public Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (mediaPlayer != null) {
                    mTvMusicTime.setText(DateUtils.formatDurationTime(mediaPlayer.getCurrentPosition()));
                    musicSeekBar.setProgress(mediaPlayer.getCurrentPosition());
                    musicSeekBar.setMax(mediaPlayer.getDuration());
                    mTvMusicTotal.setText(DateUtils.formatDurationTime(mediaPlayer.getDuration()));
                    if (mHandler != null) {
                        mHandler.postDelayed(mRunnable, 200);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public Runnable mBindRunnable = new Runnable() {
        @Override
        public void run() {
            ((PagePhoto) mList.get(1)).bindToLifecycle();
            isRunningBind = false;
        }
    };

    /**
     * ???????????????????????????
     *
     * @param path
     */
    private void initPlayer(String path) {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.setLooping(true);
            playAudio();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     */
    public class audioOnClick implements View.OnClickListener {
        private String path;

        public audioOnClick(String path) {
            super();
            this.path = path;
        }

        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.tv_PlayPause) {
                playAudio();
            }
            if (id == R.id.tv_Stop) {
                mTvMusicStatus.setText(getString(R.string.picture_stop_audio));
                mTvPlayPause.setText(getString(R.string.picture_play_audio));
                stop(path);
            }
            if (id == R.id.tv_Quit) {
                if (mHandler != null) {
                    mHandler.postDelayed(() -> stop(path), 30);
                    try {
                        if (audioDialog != null
                                && audioDialog.isShowing()) {
                            audioDialog.dismiss();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mHandler.removeCallbacks(mRunnable);
                }
            }
        }
    }

    /**
     * ????????????
     */
    private void playAudio() {
        if (mediaPlayer != null) {
            musicSeekBar.setProgress(mediaPlayer.getCurrentPosition());
            musicSeekBar.setMax(mediaPlayer.getDuration());
        }
        String ppStr = mTvPlayPause.getText().toString();
        if (ppStr.equals(getString(R.string.picture_play_audio))) {
            mTvPlayPause.setText(getString(R.string.picture_pause_audio));
            mTvMusicStatus.setText(getString(R.string.picture_play_audio));
            playOrPause();
        } else {
            mTvPlayPause.setText(getString(R.string.picture_play_audio));
            mTvMusicStatus.setText(getString(R.string.picture_pause_audio));
            playOrPause();
        }
        if (isPlayAudio == false) {
            if (mHandler != null) {
                mHandler.post(mRunnable);
            }
            isPlayAudio = true;
        }
    }

    /**
     * ????????????
     *
     * @param path
     */
    public void stop(String path) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.setDataSource(path);
                mediaPlayer.prepare();
                mediaPlayer.seekTo(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ????????????
     */
    public void playOrPause() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                } else {
                    mediaPlayer.start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onItemClick(int position, boolean isCameraFolder, long bucketId, String folderName, List<LocalMedia> images) {
        mPreviousFolderPosition = mFolderPosition;
        mFolderPosition = position;
        if (mPreviousFolderPosition != mFolderPosition) {
            isChangeFolder = true;
        } else {
            isChangeFolder = false;
        }
        boolean camera = config.isCamera && isCameraFolder;
        mAdapter.setShowCamera(camera);
        mTitle = folderName;
        mTvPictureTitle.setText(folderName);
        folderWindow.dismiss();
        mAdapter.bindImagesData(images);
        mPictureRecycler.smoothScrollToPosition(0);

        if (!images.isEmpty()) {
            //???????????????????????????
            startPreview(images, 0);
        }
    }

    @Override
    public void onItemChecked(int position, LocalMedia image, boolean isCheck) {
        if (isCheck) {
            List<LocalMedia> images = mAdapter.getImages();
            startPreview(images, position);
        } else if (mLruCache != null) {
            if (mLruCache.remove(image) == null) {
                for (Map.Entry<LocalMedia, AsyncTask> entry : mLruCache.entrySet()) {
                    if (entry.getKey().getPath().equals(image.getPath()) || entry.getKey().getId() == image.getId()) {
                        mLruCache.remove(entry.getKey());
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onTakePhoto() {
        // ??????????????????,????????????????????????????????????
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            // ???????????????????????????????????????????????????
            if (PermissionChecker
                    .checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    PermissionChecker
                            .checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                initCamera();
                if (mInstagramViewPager != null) {
                    if (mInstagramViewPager.getSelectedPosition() == 2) {
                        takeAudioPermissions();
                    }
                }
            } else {
                PermissionChecker.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, PictureConfig.APPLY_CAMERA_STORAGE_PERMISSIONS_CODE);
            }
        } else {
            PermissionChecker
                    .requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA}, PictureConfig.APPLY_CAMERA_PERMISSIONS_CODE);
        }
    }

    private void takeAudioPermissions() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {

        } else if (SPUtils.getPictureSpUtils().getBoolean(RECORD_AUDIO_PERMISSION) && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            showPermissionsDialog(true, getString(R.string.picture_audio));
        } else {
            SPUtils.getPictureSpUtils().put(RECORD_AUDIO_PERMISSION, true);
            PermissionChecker
                    .requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO}, PictureConfig.APPLY_RECORD_AUDIO_PERMISSIONS_CODE);
        }
    }

    private void initCamera() {
        if (!((PagePhoto) mList.get(1)).isBindCamera()) {
            ((PagePhoto) mList.get(1)).setEmptyViewVisibility(View.INVISIBLE);
            isRunningBind = true;
            mHandler.postDelayed(mBindRunnable, 500);
        }
    }

    @Override
    public void onChange(List<LocalMedia> selectImages) {

    }

    @Override
    public void onPictureClick(LocalMedia media, int position) {
        List<LocalMedia> images = mAdapter.getImages();
        startPreview(images, position);
    }

    /**
     * preview image and video
     *
     * @param previewImages
     * @param position
     */
    public void startPreview(List<LocalMedia> previewImages, int position) {
        if (!isChangeFolder && mPreviewPosition == position) {
            return;
        }

        RecyclerView.ViewHolder holder = mPictureRecycler.findViewHolderForAdapterPosition(position);
        if (!mInstagramGallery.isScrollTop()) {
            if (position == 0) {
                mPictureRecycler.smoothScrollToPosition(0);
            } else if (holder != null && holder.itemView != null) {
                mPictureRecycler.smoothScrollBy(0, holder.itemView.getTop());
            }
        }

        if (mInstagramGallery != null) {
            mInstagramGallery.expandPreview(new InstagramGallery.AnimationCallback() {
                @Override
                public void onAnimationStart() {

                }

                @Override
                public void onAnimationEnd() {
                    if (position == 0) {
                        mPictureRecycler.smoothScrollToPosition(0);
                    } else if (holder != null && holder.itemView != null) {
                        mPictureRecycler.smoothScrollBy(0, holder.itemView.getTop());
                    }
                }
            });
        }

        if (mPreviewPosition >= 0) {
            savePreviousPositionCropInfo(isChangeFolder ? foldersList.get(mPreviousFolderPosition).getData().get(mPreviewPosition) : previewImages.get(mPreviewPosition));
        }

        if(isChangeFolder) {
            isChangeFolder = false;
        }

        setPreviewPosition(position);

        LocalMedia media = previewImages.get(position);
        String mimeType = media.getMimeType();
        if (PictureMimeType.isHasVideo(mimeType)) {
            // video
            mPreviewContainer.checkModel(InstagramPreviewContainer.PLAY_VIDEO_MODE);
            mPreviewContainer.playVideo(media, holder);
        } else if (PictureMimeType.isHasAudio(mimeType)) {
            // audio
            audioDialog(media.getPath());
        } else {
            // image
            if (media != null) {
                mPreviewContainer.checkModel(InstagramPreviewContainer.PLAY_IMAGE_MODE);
                final String path;
//                if (media.isCut() && !media.isCompressed()) {
//                    // ?????????
//                    path = media.getCutPath();
//                } else if (media.isCompressed() || (media.isCut() && media.isCompressed())) {
//                    // ?????????,???????????????????????????,??????????????????????????????
//                    path = media.getCompressPath();
//                } else {
                    path = media.getPath();
//                }
                boolean isHttp = PictureMimeType.isHasHttp(path);
                boolean isAndroidQ = SdkVersionUtils.checkedAndroid_Q();
                Uri uri = isHttp || isAndroidQ ? Uri.parse(path) : Uri.fromFile(new File(path));
                String suffix = mimeType.replace("image/", ".");
                File file = new File(PictureFileUtils.getDiskCacheDir(this),
                        TextUtils.isEmpty(config.renameCropFileName) ? DateUtils.getCreateFileName("IMG_") + suffix : config.renameCropFileName);
                mPreviewContainer.setImageUri(uri, Uri.fromFile(file));
            }
        }
    }

    private void savePreviousPositionCropInfo(LocalMedia previousMedia) {
        if (previousMedia == null || mLruCache == null || mPreviewContainer == null || config.selectionMode == PictureConfig.SINGLE || !PictureMimeType.isHasImage(previousMedia.getMimeType())) {
            return;
        }

        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
        if (selectedImages.contains(previousMedia)) {
            mLruCache.put(previousMedia, mPreviewContainer.createCropAndSaveImageTask(new BitmapCropCallbackImpl(previousMedia)));
        } else {
            for (LocalMedia selectedImage : selectedImages) {
                if (selectedImage.getPath().equals(previousMedia.getPath()) || selectedImage.getId() == previousMedia.getId()) {
                    mLruCache.put(selectedImage, mPreviewContainer.createCropAndSaveImageTask(new BitmapCropCallbackImpl(selectedImage)));
                    break;
                }
            }
        }
    }

    private void startMultiCrop() {
        if (mLruCache == null || mAdapter == null || mPreviewContainer == null || isCroppingImage) {
            return;
        }
        isCroppingImage = true;
        showPleaseDialog();
        for (Map.Entry<LocalMedia, AsyncTask> entry : mLruCache.entrySet()) {
            Objects.requireNonNull((BitmapCropTask) entry.getValue()).execute();
        }
        new FinishMultiCropTask(this, mPreviewContainer, mAdapter.getSelectedImages(), config).execute();
    }

    public static class FinishMultiCropTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<InstagramPreviewContainer> mContainerWeakReference;
        private WeakReference<PictureSelectorInstagramStyleActivity> mActivityWeakReference;
        private List<LocalMedia> mSelectedImages;
        private PictureSelectionConfig mConfig;

        public FinishMultiCropTask(PictureSelectorInstagramStyleActivity activity, InstagramPreviewContainer previewContainer, List<LocalMedia> selectedImages, PictureSelectionConfig config) {
            mContainerWeakReference = new WeakReference<>(previewContainer);
            mActivityWeakReference = new WeakReference<>(activity);
            mSelectedImages = selectedImages;
            mConfig = config;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            InstagramPreviewContainer previewContainer = mContainerWeakReference.get();
            List<LocalMedia> result = new ArrayList<>();
            result.addAll(mSelectedImages);

            Bundle bundle = null;
            if (previewContainer != null) {
                bundle = new Bundle();
                bundle.putBoolean(InstagramMediaProcessActivity.EXTRA_ASPECT_RATIO, previewContainer.isAspectRatio());
            }

            PictureSelectorInstagramStyleActivity activity = mActivityWeakReference.get();
            if (activity != null) {
                activity.dismissDialog();
                InstagramMediaProcessActivity.launchActivity(activity, mConfig, result, bundle, InstagramMediaProcessActivity.REQUEST_MULTI_IMAGE_PROCESS);
            }
        }
    }

    private static class BitmapCropCallbackImpl implements BitmapCropCallback {
        private LocalMedia mLocalMedia;

        public BitmapCropCallbackImpl(LocalMedia localMedia) {
            mLocalMedia = localMedia;
        }

        public void setLocalMedia(LocalMedia localMedia) {
            mLocalMedia = localMedia;
        }

        @Override
        public void onBitmapCropped(@NonNull Uri resultUri, int offsetX, int offsetY, int imageWidth, int imageHeight) {
            if (mLocalMedia != null) {
                mLocalMedia.setCut(true);
                mLocalMedia.setCutPath(resultUri.getPath());
                mLocalMedia.setWidth(imageWidth);
                mLocalMedia.setHeight(imageHeight);
                mLocalMedia.setSize(new File(resultUri.getPath()).length());
                mLocalMedia.setAndroidQToPath(SdkVersionUtils.checkedAndroid_Q() ? resultUri.getPath() : mLocalMedia.getAndroidQToPath());
            }
        }

        @Override
        public void onCropFailure(@NonNull Throwable t) {
            t.printStackTrace();
        }
    }


    private void setPreviewPosition(int position) {
        if (mPreviewPosition != position && mAdapter != null && position < mAdapter.getItemCount()) {
            int previousPosition = mPreviewPosition;
            mPreviewPosition = position;
            mAdapter.setPreviewPosition(position);
            mAdapter.notifyItemChanged(previousPosition);
            mAdapter.notifyItemChanged(position);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case PictureConfig.PREVIEW_VIDEO_CODE:
                    if (data != null) {
                        List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
                        if (list != null && list.size() > 0) {
                            onResult(list);
                        }
                    }
                    break;
                case UCrop.REQUEST_CROP:
                    singleImageFilterHandle(data);
                    break;
                case InstagramMediaProcessActivity.REQUEST_SINGLE_IMAGE_PROCESS:
                    singleCropHandleResult(data);
                    break;
                case InstagramMediaProcessActivity.REQUEST_MULTI_IMAGE_PROCESS:
                    if (mAdapter.getSelectedImages().size() > 1) {
                        if (data != null) {
                            List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
                            if (list != null) {
                                mAdapter.bindSelectImages(list);
                                mAdapter.notifyDataSetChanged();
                            }
                        }
                        handlerResult(mAdapter.getSelectedImages());
                    } else {
                        singleCropHandleResult(data);
                    }
                    break;
                case InstagramMediaProcessActivity.REQUEST_SINGLE_VIDEO_PROCESS:
                    if (data != null) {
                        List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
                        if (list != null) {
                            mAdapter.bindSelectImages(list);
                            mAdapter.notifyDataSetChanged();
                        }
                    }
                    onResult(mAdapter.getSelectedImages());
                    break;
                case UCrop.REQUEST_MULTI_CROP:
                    multiCropHandleResult(data);
                    break;
                case PictureConfig.REQUEST_CAMERA:
                    requestCamera(data);
                    break;
                default:
                    break;
            }
        } else if (resultCode == RESULT_CANCELED) {
            previewCallback(data);
        } else if (resultCode == UCrop.RESULT_ERROR) {
            if (data != null) {
                Throwable throwable = (Throwable) data.getSerializableExtra(UCrop.EXTRA_ERROR);
                ToastUtils.s(getContext(), throwable.getMessage());
            }
        } else if (resultCode == InstagramMediaProcessActivity.RESULT_MEDIA_PROCESS_CANCELED) {
            List<LocalMedia> result = mAdapter.getSelectedImages();
            if (!result.isEmpty()) {
                result.clear();
            }
            if (mLruCache != null && mLruCache.size() > 0) {
                mLruCache.clear();
            }
            if (mPreviewContainer != null && mPreviewContainer.isMulti()) {
                mPreviewContainer.setMultiMode(false);
            }
            isCroppingImage = false;
        }
    }

    private void singleImageFilterHandle(Intent data) {
        List<LocalMedia> result = new ArrayList<>();
        if (mAdapter.getSelectedImages().size() > 0) {
            result.add(mAdapter.getSelectedImages().get(0));
        }

        Bundle bundle = new Bundle();
        if (data != null && !result.isEmpty()) {
            LocalMedia media = result.get(0);
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                media.setCut(true);
                media.setCutPath(resultUri.getPath());
            }
        }

        if (mPreviewContainer != null && !data.getBooleanExtra(BitmapCropSquareTask.EXTRA_FROM_CAMERA, false)) {
            bundle.putBoolean(InstagramMediaProcessActivity.EXTRA_ASPECT_RATIO, mPreviewContainer.isAspectRatio());
        }

        InstagramMediaProcessActivity.launchActivity(this, config, result, bundle, InstagramMediaProcessActivity.REQUEST_SINGLE_IMAGE_PROCESS);
    }

    /**
     * ????????????????????????
     *
     * @param data
     */
    private void previewCallback(Intent data) {
        if (data == null) {
            return;
        }
        // ??????????????????????????????????????????????????????
        List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
        if (mAdapter != null && list != null) {
            // ?????????????????????????????????????????????????????????????????????
            boolean isCompleteOrSelected = data.getBooleanExtra(PictureConfig.EXTRA_COMPLETE_SELECTED, false);
            if (isCompleteOrSelected) {
                onChangeData(list);
                if (config.isWithVideoImage) {
                    // ????????????
                    int size = list.size();
                    int imageSize = 0;
                    for (int i = 0; i < size; i++) {
                        LocalMedia media = list.get(i);
                        if (PictureMimeType.isHasImage(media.getMimeType())) {
                            imageSize++;
                            break;
                        }
                    }
                    if (imageSize <= 0 || !config.isCompress || config.isCheckOriginalImage) {
                        // ????????????
                        onResult(list);
                    } else {
                        // ?????????
                        compressImage(list);
                    }
                } else {
                    // ?????????1?????????????????????????????????????????????????????????????????????????????????????????????
                    String mimeType = list.size() > 0 ? list.get(0).getMimeType() : "";
                    if (config.isCompress && PictureMimeType.isHasImage(mimeType)
                            && !config.isCheckOriginalImage) {
                        compressImage(list);
                    } else {
                        onResult(list);
                    }
                }
            }
            mAdapter.bindSelectImages(list);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param list
     */
    protected void onChangeData(List<LocalMedia> list) {

    }

    /**
     * singleDirectReturn??????????????????????????????
     *
     * @param mimeType
     */
    private void singleDirectReturnCameraHandleResult(String mimeType) {
        boolean eqImg = PictureMimeType.isHasImage(mimeType);
        if (config.enableCrop && eqImg) {
            // ?????????
            config.originalPath = config.cameraPath;
//            startCrop(config.cameraPath, mimeType);
            startSingleCrop(config.cameraPath, mimeType);
        } else if (config.isCompress && eqImg) {
            // ?????????
            List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
            compressImage(selectedImages);
        } else {
            // ????????? ????????? ??????????????????
            onResult(mAdapter.getSelectedImages());
        }
    }

    protected void startSingleCrop(String originalPath, String mimeType) {
        if (DoubleUtils.isFastDoubleClick()) {
            return;
        }
        if (TextUtils.isEmpty(originalPath)) {
            ToastUtils.s(this, getString(R.string.picture_not_crop_data));
            return;
        }

        boolean isHttp = PictureMimeType.isHasHttp(originalPath);
        String suffix = mimeType.replace("image/", ".");
        File file = new File(PictureFileUtils.getDiskCacheDir(getContext()),
                TextUtils.isEmpty(config.renameCropFileName) ? DateUtils.getCreateFileName("IMG_CROP_") + suffix : config.renameCropFileName);
        Uri uri = isHttp || SdkVersionUtils.checkedAndroid_Q() ? Uri.parse(originalPath) : Uri.fromFile(new File(originalPath));
        Uri destination = Uri.fromFile(file);

        int maxBitmapSize = BitmapLoadUtils.calculateMaxBitmapSize(getContext());
        BitmapLoadUtils.decodeBitmapInBackground(getContext(), uri, destination, maxBitmapSize, maxBitmapSize,
                new BitmapLoadCallback() {
                    @Override
                    public void onBitmapLoaded(@NonNull Bitmap bitmap, @NonNull ExifInfo exifInfo, @NonNull String imageInputPath, @Nullable String imageOutputPath) {
                        new BitmapCropSquareTask(bitmap, imageOutputPath, PictureSelectorInstagramStyleActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }

                    @Override
                    public void onFailure(@NonNull Exception bitmapWorkerException) {
                        ToastUtils.s(getContext(), bitmapWorkerException.getMessage());
                    }
                });
    }

    /**
     * ?????????????????????
     *
     * @param data
     */

    private void requestCamera(Intent data) {
        // on take photo success
        String mimeType = null;
        long duration = 0;
        boolean isAndroidQ = SdkVersionUtils.checkedAndroid_Q();
        if (config.chooseMode == PictureMimeType.ofAudio()) {
            // ??????????????????
            config.cameraPath = getAudioPath(data);
            if (TextUtils.isEmpty(config.cameraPath)) {
                return;
            }
            mimeType = PictureMimeType.MIME_TYPE_AUDIO;
            duration = MediaUtils.extractDuration(getContext(), isAndroidQ, config.cameraPath);
        }
        if (TextUtils.isEmpty(config.cameraPath) || new File(config.cameraPath) == null) {
            return;
        }
        long size = 0;
        int[] newSize = new int[2];
        if (!isAndroidQ) {
            if (config.isFallbackVersion3) {
                new PictureMediaScannerConnection(getContext(), config.cameraPath);
            } else {
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(config.cameraPath))));
            }
        }
        LocalMedia media = new LocalMedia();
        if (config.chooseMode != PictureMimeType.ofAudio()) {
            // ????????????????????????
            if (PictureMimeType.isContent(config.cameraPath)) {
                String path = PictureFileUtils.getPath(getApplicationContext(), Uri.parse(config.cameraPath));
                if (!TextUtils.isEmpty(path)) {
                    File file = new File(path);
                    size = file.length();
                    mimeType = PictureMimeType.getMimeType(file);
                }
                if (PictureMimeType.isHasImage(mimeType)) {
                    newSize = MediaUtils.getImageSizeForUrlToAndroidQ(this, config.cameraPath);
                } else {
                    newSize = MediaUtils.getVideoSizeForUri(this, Uri.parse(config.cameraPath));
                    duration = MediaUtils.extractDuration(getContext(), true, config.cameraPath);
                }
                int lastIndexOf = config.cameraPath.lastIndexOf("/") + 1;
                media.setId(lastIndexOf > 0 ? ValueOf.toLong(config.cameraPath.substring(lastIndexOf)) : -1);
                media.setRealPath(path);
                if (config.isUseCustomCamera) {
                    // ?????????????????????????????????????????????????????????
                    if (data != null) {
                        String mediaPath = data.getStringExtra(PictureConfig.EXTRA_MEDIA_PATH);
                        media.setAndroidQToPath(mediaPath);
                    }
                }
            } else {
                File file = new File(config.cameraPath);
                mimeType = PictureMimeType.getMimeType(file);
                size = file.length();
                if (PictureMimeType.isHasImage(mimeType)) {
                    int degree = PictureFileUtils.readPictureDegree(this, config.cameraPath);
                    BitmapUtils.rotateImage(degree, config.cameraPath);
                    newSize = MediaUtils.getImageSizeForUrl(config.cameraPath);
                } else {
                    newSize = MediaUtils.getVideoSizeForUrl(config.cameraPath);
                    duration = MediaUtils.extractDuration(getContext(), false, config.cameraPath);
                }
                // ????????????????????????id
                media.setId(System.currentTimeMillis());
            }
        }
        media.setDuration(duration);
        media.setWidth(newSize[0]);
        media.setHeight(newSize[1]);
        media.setPath(config.cameraPath);
        media.setMimeType(mimeType);
        media.setSize(size);
        media.setChooseModel(config.chooseMode);
        if (mAdapter != null) {
            boolean isPreview = true;
            images.add(0, media);
            if (checkVideoLegitimacy(media)) {
                if (config.selectionMode == PictureConfig.SINGLE) {
                    // ?????????????????????????????????
                    if (config.isSingleDirectReturn) {
                        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                        if (selectedImages.size() > 0) {
                            selectedImages.clear();
                        }
                        selectedImages.add(media);
                        mAdapter.bindSelectImages(selectedImages);
                        singleDirectReturnCameraHandleResult(mimeType);
                    } else {
                        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                        mimeType = selectedImages.size() > 0 ? selectedImages.get(0).getMimeType() : "";
                        boolean mimeTypeSame = PictureMimeType.isMimeTypeSame(mimeType, media.getMimeType());
                        // ??????????????????????????????????????????????????????
                        if (mimeTypeSame || selectedImages.size() == 0) {
                            // ??????????????????????????????????????????????????????(???????????????)
                            singleRadioMediaImage();
                            selectedImages.add(media);
                            mAdapter.bindSelectImages(selectedImages);
                        }
                    }
                } else {
                    // ????????????
                    List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                    int count = selectedImages.size();
                    mimeType = count > 0 ? selectedImages.get(0).getMimeType() : "";
                    boolean mimeTypeSame = PictureMimeType.isMimeTypeSame(mimeType, media.getMimeType());
                    if (config.isWithVideoImage) {
                        // ????????????
                        int videoSize = 0;
                        int imageSize = 0;
                        for (int i = 0; i < count; i++) {
                            LocalMedia m = selectedImages.get(i);
                            if (PictureMimeType.isHasVideo(m.getMimeType())) {
                                videoSize++;
                            } else {
                                imageSize++;
                            }
                        }
                        if (PictureMimeType.isHasVideo(media.getMimeType()) && config.maxVideoSelectNum > 0) {
                            // ???????????????
                            if (videoSize < config.maxVideoSelectNum) {
                                selectedImages.add(0, media);
                                mAdapter.bindSelectImages(selectedImages);
                            } else {
                                isPreview = false;
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), media.getMimeType(),
                                        config.maxVideoSelectNum));
                            }
                        } else {
                            // ???????????????
                            if (imageSize < config.maxSelectNum) {
                                selectedImages.add(0, media);
                                mAdapter.bindSelectImages(selectedImages);
                            } else {
                                isPreview = false;
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), media.getMimeType(),
                                        config.maxSelectNum));
                            }
                        }

                    } else {
                        if (PictureMimeType.isHasVideo(mimeType) && config.maxVideoSelectNum > 0) {
                            // ??????????????????????????????????????????????????????
                            if (count < config.maxVideoSelectNum) {
                                if (mimeTypeSame || count == 0) {
                                    if (selectedImages.size() < config.maxVideoSelectNum) {
                                        selectedImages.add(0, media);
                                        mAdapter.bindSelectImages(selectedImages);
                                    }
                                }
                            } else {
                                isPreview = false;
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), mimeType,
                                        config.maxVideoSelectNum));
                            }
                        } else {
                            // ???????????????????????? ??????????????????????????????
                            if (count < config.maxSelectNum) {
                                // ??????????????????????????????????????????????????????
                                if (mimeTypeSame || count == 0) {
                                    selectedImages.add(0, media);
                                    mAdapter.bindSelectImages(selectedImages);
                                }
                            } else {
                                isPreview = false;
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), mimeType,
                                        config.maxSelectNum));
                            }
                        }
                    }
                }
            }
            LocalMediaFolder localMediaFolder = foldersList.get(mFolderPosition);
            if (localMediaFolder.isCameraFolder() || "Camera".equals(localMediaFolder.getName())) {
                mAdapter.notifyItemInserted(config.isCamera ? 1 : 0);
                mAdapter.notifyItemRangeChanged(config.isCamera ? 1 : 0, images.size());
                if (images.size() == 1 || (config.selectionMode != PictureConfig.SINGLE && isPreview)) {
                    startPreview(images, 0);
                } else {
                    setPreviewPosition(mPreviewPosition + 1);
                }
            }
            // ???????????????????????????Intent.ACTION_MEDIA_SCANNER_SCAN_FILE????????????????????????????????????
            manualSaveFolder(media);
            // ?????????????????????????????????????????????DCIM????????????????????????????????????
            if (!isAndroidQ && PictureMimeType.isHasImage(media.getMimeType())) {
                int lastImageId = getLastImageId(media.getMimeType());
                if (lastImageId != -1) {
                    removeMedia(lastImageId);
                }
            }
            mTvEmpty.setVisibility(images.size() > 0 ? View.INVISIBLE : View.VISIBLE);
            mInstagramGallery.setViewVisibility(images.size() > 0 ? View.VISIBLE : View.INVISIBLE);
            boolean enabled = images.size() > 0 || config.returnEmpty;
            mTvPictureRight.setEnabled(enabled);
            mTvPictureRight.setTextColor(enabled ? config.style.pictureRightDefaultTextColor : ContextCompat.getColor(getContext(), R.color.picture_color_9B9B9D));
        }
    }

    /**
     * ????????????????????????
     *
     * @param media
     * @return
     */
    private boolean checkVideoLegitimacy(LocalMedia media) {
        boolean isEnterNext = true;
        if (PictureMimeType.isHasVideo(media.getMimeType())) {
            // ??????????????????????????????
            if (config.videoMinSecond > 0 && config.videoMaxSecond > 0) {
                // ??????????????????????????????????????????????????????????????????????????????
                if (media.getDuration() < config.videoMinSecond || media.getDuration() > config.videoMaxSecond) {
                    isEnterNext = false;
                    ToastUtils.s(getContext(), getString(R.string.picture_choose_limit_seconds, config.videoMinSecond / 1000, config.videoMaxSecond / 1000));
                }
            } else if (config.videoMinSecond > 0 && config.videoMaxSecond <= 0) {
                // ??????????????????????????????????????????
                if (media.getDuration() < config.videoMinSecond) {
                    isEnterNext = false;
                    ToastUtils.s(getContext(), getString(R.string.picture_choose_min_seconds, config.videoMinSecond / 1000));
                }
            } else if (config.videoMinSecond <= 0 && config.videoMaxSecond > 0) {
                // ??????????????????????????????????????????
                if (media.getDuration() > config.videoMaxSecond) {
                    isEnterNext = false;
                    ToastUtils.s(getContext(), getString(R.string.picture_choose_max_seconds, config.videoMaxSecond / 1000));
                }
            }
        }
        return isEnterNext;
    }

    /**
     * ??????????????????
     *
     * @param data
     */
    private void singleCropHandleResult(Intent data) {
        if (data == null) {
            return;
        }
        List<LocalMedia> result = new ArrayList<>();
        Uri resultUri = UCrop.getOutput(data);
        String cutPath = resultUri.getPath();
        if (mAdapter != null) {
            List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
            if (list != null) {
                mAdapter.bindSelectImages(list);
                mAdapter.notifyDataSetChanged();
            }
            // ?????????????????????????????????path????????????
            List<LocalMedia> mediaList = mAdapter.getSelectedImages();
            LocalMedia media = mediaList != null && mediaList.size() > 0 ? mediaList.get(0) : null;
            if (media != null) {
                config.originalPath = media.getPath();
                media.setCutPath(cutPath);
                media.setChooseModel(config.chooseMode);
                if (TextUtils.isEmpty(cutPath)) {
                    if (SdkVersionUtils.checkedAndroid_Q()
                            && PictureMimeType.isContent(media.getPath())) {
                        String path = PictureFileUtils.getPath(this, Uri.parse(media.getPath()));
                        media.setSize(new File(path).length());
                    } else {
                        media.setSize(new File(media.getPath()).length());
                    }
                    media.setCut(false);
                } else {
                    media.setSize(new File(cutPath).length());
                    media.setAndroidQToPath(cutPath);
                    media.setCut(true);
                }
                result.add(media);
                handlerResult(result);
            } else {
                // ??????????????????????????????????????????
                media = list != null && list.size() > 0 ? list.get(0) : null;
                config.originalPath = media.getPath();
                media.setCutPath(cutPath);
                media.setChooseModel(config.chooseMode);
                media.setSize(new File(TextUtils.isEmpty(cutPath)
                        ? media.getPath() : cutPath).length());
                if (TextUtils.isEmpty(cutPath)) {
                    if (SdkVersionUtils.checkedAndroid_Q()
                            && PictureMimeType.isContent(media.getPath())) {
                        String path = PictureFileUtils.getPath(this, Uri.parse(media.getPath()));
                        media.setSize(new File(path).length());
                    } else {
                        media.setSize(new File(media.getPath()).length());
                    }
                    media.setCut(false);
                } else {
                    media.setSize(new File(cutPath).length());
                    media.setAndroidQToPath(cutPath);
                    media.setCut(true);
                }
                result.add(media);
                handlerResult(result);
            }
        }
    }

    /**
     * ??????????????????
     *
     * @param data
     */
    protected void multiCropHandleResult(Intent data) {
        if (data == null) {
            return;
        }
        List<CutInfo> mCuts = UCrop.getMultipleOutput(data);
        if (mCuts == null || mCuts.size() == 0) {
            return;
        }
        int size = mCuts.size();
        boolean isAndroidQ = SdkVersionUtils.checkedAndroid_Q();
        List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
        if (list != null) {
            mAdapter.bindSelectImages(list);
            mAdapter.notifyDataSetChanged();
        }
        int oldSize = mAdapter != null ? mAdapter.getSelectedImages().size() : 0;
        if (oldSize == size) {
            List<LocalMedia> result = mAdapter.getSelectedImages();
            for (int i = 0; i < size; i++) {
                CutInfo c = mCuts.get(i);
                LocalMedia media = result.get(i);
                media.setCut(!TextUtils.isEmpty(c.getCutPath()));
                media.setPath(c.getPath());
                media.setMimeType(c.getMimeType());
                media.setCutPath(c.getCutPath());
                media.setWidth(c.getImageWidth());
                media.setHeight(c.getImageHeight());
                media.setAndroidQToPath(isAndroidQ ? c.getCutPath() : media.getAndroidQToPath());
                media.setSize(!TextUtils.isEmpty(c.getCutPath()) ? new File(c.getCutPath()).length() : media.getSize());
            }
            handlerResult(result);
        } else {
            // ????????????
            List<LocalMedia> result = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                CutInfo c = mCuts.get(i);
                LocalMedia media = new LocalMedia();
                media.setId(c.getId());
                media.setCut(!TextUtils.isEmpty(c.getCutPath()));
                media.setPath(c.getPath());
                media.setCutPath(c.getCutPath());
                media.setMimeType(c.getMimeType());
                media.setWidth(c.getImageWidth());
                media.setHeight(c.getImageHeight());
                media.setDuration(c.getDuration());
                media.setChooseModel(config.chooseMode);
                media.setAndroidQToPath(isAndroidQ ? c.getCutPath() : null);
                if (!TextUtils.isEmpty(c.getCutPath())) {
                    media.setSize(new File(c.getCutPath()).length());
                } else {
                    if (SdkVersionUtils.checkedAndroid_Q() && PictureMimeType.isContent(c.getPath())) {
                        String path = PictureFileUtils.getPath(this, Uri.parse(c.getPath()));
                        media.setSize(new File(path).length());
                    } else {
                        media.setSize(new File(c.getPath()).length());
                    }
                }
                result.add(media);
            }
            handlerResult(result);
        }
    }

    /**
     * ????????????
     */
    private void singleRadioMediaImage() {
        List<LocalMedia> selectImages = mAdapter.getSelectedImages();
        if (selectImages != null
                && selectImages.size() > 0) {
            LocalMedia media = selectImages.get(0);
            int position = media.getPosition();
            selectImages.clear();
            mAdapter.notifyItemChanged(position);
        }
    }

    /**
     * ???????????????????????????????????????????????????????????????
     *
     * @param media
     */
    private void manualSaveFolder(LocalMedia media) {
        try {
            createNewFolder(foldersList);
            LocalMediaFolder folder = getImageFolder(media.getPath(), media.getRealPath(), foldersList);
            LocalMediaFolder cameraFolder = foldersList.size() > 0 ? foldersList.get(0) : null;
            if (cameraFolder != null && folder != null) {
                media.setParentFolderName(folder.getName());
                // ????????????
                cameraFolder.setFirstImagePath(media.getPath());
                cameraFolder.setData(images);
                cameraFolder.setImageNum(cameraFolder.getImageNum() + 1);
                // ????????????
                int num = folder.getImageNum() + 1;
                folder.setImageNum(num);
                folder.getData().add(0, media);
                folder.setFirstImagePath(config.cameraPath);
                folderWindow.bindFolder(foldersList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     *
     * @param imageFolders
     */
    private void updateMediaFolder(List<LocalMediaFolder> imageFolders, LocalMedia media) {
        File imageFile = new File(PictureMimeType.isContent(media.getPath())
                ? Objects.requireNonNull(PictureFileUtils.getPath(getContext(), Uri.parse(media.getPath()))) : media.getPath());
        File folderFile = imageFile.getParentFile();
        int size = imageFolders.size();
        for (int i = 0; i < size; i++) {
            LocalMediaFolder folder = imageFolders.get(i);
            // ???????????????????????????????????????????????????????????????
            String name = folder.getName();
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            if (name.equals(folderFile.getName())) {
                folder.setFirstImagePath(config.cameraPath);
                folder.setImageNum(folder.getImageNum() + 1);
                folder.setCheckedNum(1);
                folder.getData().add(0, media);
                break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mInstagramViewPager.getSelectedPosition() == 2 && ((PagePhoto) mList.get(1)).isInLongPress()) {
            return;
        }
        super.onBackPressed();
        if (config != null && PictureSelectionConfig.listener != null) {
            PictureSelectionConfig.listener.onCancel();
        }
        closeActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null && mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
            mHandler.removeCallbacks(mBindRunnable);
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mPreviewContainer != null) {
            mPreviewContainer.onDestroy();
        }
        if (mInstagramViewPager != null) {
            mInstagramViewPager.onDestroy();
        }
    }

    @Override
    public void onItemClick(View v, int position) {
        switch (position) {
            case PhotoItemSelectedDialog.IMAGE_CAMERA:
                // ??????
                if (PictureSelectionConfig.onCustomCameraInterfaceListener != null) {
                    PictureSelectionConfig.onCustomCameraInterfaceListener.onCameraClick(getContext(), config, PictureConfig.TYPE_IMAGE);
                } else {
                    startOpenCamera();
                }
                break;
            case PhotoItemSelectedDialog.VIDEO_CAMERA:
                // ?????????
                if (PictureSelectionConfig.onCustomCameraInterfaceListener != null) {
                    PictureSelectionConfig.onCustomCameraInterfaceListener.onCameraClick(getContext(), config, PictureConfig.TYPE_VIDEO);
                } else {
                    startOpenCameraVideo();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PictureConfig.APPLY_STORAGE_PERMISSIONS_CODE:
                // ????????????
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    readLocalMedia();
                } else {
                    showPermissionsDialog(false, getString(R.string.picture_jurisdiction));
                }
                break;
            case PictureConfig.APPLY_CAMERA_PERMISSIONS_CODE:
                // ????????????
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onTakePhoto();
                } else {
                    ((PagePhoto) mList.get(1)).setEmptyViewVisibility(View.VISIBLE);
                }
                break;
            case PictureConfig.APPLY_CAMERA_STORAGE_PERMISSIONS_CODE:
                // ?????????????????????????????????
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initCamera();
                    if (mInstagramViewPager != null) {
                        if (mInstagramViewPager.getSelectedPosition() == 2) {
                            takeAudioPermissions();
                        }
                    }
                } else {
                    showPermissionsDialog(false, getString(R.string.picture_jurisdiction));
                }
                break;
            case PictureConfig.APPLY_RECORD_AUDIO_PERMISSIONS_CODE:
                // ????????????
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takeAudioPermissions();
                } else {

                }
                break;
        }
    }

    @Override
    protected void showPermissionsDialog(boolean isCamera, String errorMsg) {
        if (isFinishing()) {
            return;
        }
        final PictureCustomDialog dialog =
                new PictureCustomDialog(getContext(), R.layout.picture_wind_base_dialog);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        Button btn_cancel = dialog.findViewById(R.id.btn_cancel);
        Button btn_commit = dialog.findViewById(R.id.btn_commit);
        btn_commit.setText(getString(R.string.picture_go_setting));
        TextView tv_title = dialog.findViewById(R.id.tvTitle);
        TextView tv_content = dialog.findViewById(R.id.tv_content);
        tv_title.setText(getString(R.string.picture_prompt));
        tv_content.setText(errorMsg);
        btn_cancel.setOnClickListener(v -> {
            if (!isFinishing()) {
                dialog.dismiss();
            }
            if (!isCamera) {
                closeActivity();
            }
        });
        btn_commit.setOnClickListener(v -> {
            if (!isFinishing()) {
                dialog.dismiss();
            }
            PermissionChecker.launchAppDetailsSettings(getContext());
            isEnterSetting = true;
        });
        dialog.show();
    }
}
