package de.rawinstinctai.apkdrop;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** An explicit initial when the publisher has supplied no image. */
final class AppPlaceholder extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String initial;
    AppPlaceholder(String name){initial=name==null||name.isBlank()?"A":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(java.util.Locale.ROOT);}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();paint.setColor(Color.rgb(38,44,31));canvas.drawRoundRect(new RectF(b),b.width()*.22f,b.width()*.22f,paint);
        paint.setColor(Color.rgb(217,245,109));paint.setTextSize(b.height()*.46f);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(initial,b.exactCenterX(),b.exactCenterY()-(paint.ascent()+paint.descent())/2,paint);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
