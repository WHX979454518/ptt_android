package com.zrt.ptt.app.console.mvp.view.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.telecom.RemoteConnection;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import com.baidu.mapapi.model.inner.Point;

/**
 * Created by mycow on 2017/5/21.
 */

public class MapRectSelectView extends TextureView
        implements TextureView.SurfaceTextureListener, View.OnTouchListener{

    private Point startPoint;
    private Point endPoint;
    private Paint paint;
    private RectSelectedCallBack rectSelectedCallBack;

    public MapRectSelectView(Context context) {
        super(context);

        paint = new Paint();
        paint.setColor(Color.RED);

        setOnTouchListener(this);
        setSurfaceTextureListener(this);
    }

    public MapRectSelectView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.RED);
        startPoint = new Point();
        endPoint = new Point();

        setOnTouchListener(this);
        setSurfaceTextureListener(this);
    }

    private void  clear()
    {
        Canvas canvas = lockCanvas();

        canvas.drawColor(Color.BLACK);
        canvas.drawText("框选：请在地图上拖动", 0,"框选：请在地图上拖动".length(), 20, 20, paint);

        unlockCanvasAndPost(canvas);
    }

    private void  draw()
    {
        Canvas canvas = lockCanvas();

        canvas.drawColor(Color.BLACK);
        canvas.drawRect(startPoint.x, startPoint.y, endPoint.x, endPoint.y, paint);

        unlockCanvasAndPost(canvas);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
            {
                startPoint.x = (int) event.getX();
                startPoint.y = (int) event.getY();
                endPoint.x = (int) event.getX();
                endPoint.y = (int) event.getY();
                draw();
            }
            break;
            case MotionEvent.ACTION_MOVE:
            {
                endPoint.x = (int) event.getX();
                endPoint.y = (int) event.getY();
                draw();
            }
            break;
            case MotionEvent.ACTION_UP:
            {
                if (rectSelectedCallBack != null)
                {
                    rectSelectedCallBack.rectSelected(startPoint, endPoint);
                }
                clear();
            }
                break;
        }
        return true;
    }

    public void addRectSelectedCallBack(RectSelectedCallBack cb)
    {
        rectSelectedCallBack = cb;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        clear();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    public interface RectSelectedCallBack{
        void rectSelected(Point startPoint, Point endPoint);
    }
}
