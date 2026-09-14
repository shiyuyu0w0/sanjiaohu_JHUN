package cn.jhun.sanjiaohu;

/** Image-space rotation and bounded viewport gestures, independent of Android. */
final class CalendarViewport {
    final float imageWidth,imageHeight;
    float width,height,zoom=1,panX,panY;
    int quarterTurns=3; // The supplied calendar is sideways; open it upright.
    CalendarViewport(float imageWidth,float imageHeight){this.imageWidth=imageWidth;this.imageHeight=imageHeight;}
    float rotatedWidth(){return quarterTurns%2==0?imageWidth:imageHeight;}
    float rotatedHeight(){return quarterTurns%2==0?imageHeight:imageWidth;}
    float scale(){return Math.min(width/rotatedWidth(),height/rotatedHeight())*zoom;}
    void resize(float width,float height){this.width=width;this.height=height;panX=panY=0;bound();}
    void fit(){zoom=1;panX=panY=0;}
    void rotate(){quarterTurns=(quarterTurns+1)%4;fit();}
    void pan(float dx,float dy){panX+=dx;panY+=dy;bound();}
    void zoomBy(float factor,float focusX,float focusY){
        if(!Float.isFinite(factor)||factor<=0)return;
        float next=Math.max(1,Math.min(12,zoom*factor)),ratio=next/zoom;
        panX=focusX-width/2-(focusX-width/2-panX)*ratio;
        panY=focusY-height/2-(focusY-height/2-panY)*ratio;
        zoom=next;bound();
    }
    void bound(){
        float maxX=Math.max(0,(rotatedWidth()*scale()-width)/2),maxY=Math.max(0,(rotatedHeight()*scale()-height)/2);
        panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));
    }
}
