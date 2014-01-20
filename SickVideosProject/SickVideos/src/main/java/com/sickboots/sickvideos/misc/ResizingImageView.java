package com.sickboots.sickvideos.misc;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by sgehrman on 1/17/14.
 */
public class ResizingImageView extends ImageView {

  public ResizingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    // fix odd behavior on JellyBean (<=17) See ImageView android:adjustViewBounds for notes on this
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      Drawable drawable = getDrawable();
      if (drawable != null) {
        int width = MeasureSpec.getSize(widthMeasureSpec);

        float ratio = drawable.getIntrinsicHeight() / drawable.getIntrinsicWidth();
        int newHeight = (int) (MeasureSpec.getSize(widthMeasureSpec) * ratio);

        setMeasuredDimension(width, newHeight);
      }
    }
  }
}