package cn.jhun.sanjiaohu;

/** Crop frame in preview coordinates; exported bounds are always inside the source. */
final class WallpaperCropGeometry {
    final float imageWidth,imageHeight;
    float width,height,left,top,right,bottom,imageX,imageY,scale,ratio;
    WallpaperCropGeometry(float imageWidth,float imageHeight,float ratio){this.imageWidth=imageWidth;this.imageHeight=imageHeight;this.ratio=ratio;}
    float cropWidth(){return right-left;}
    float cropHeight(){return bottom-top;}
    void resize(float w,float h){float[] selection=scale>0?source():null;width=w;height=h;if(w<=0||h<=0)return;if(selection==null)reset();else restore(selection);}
    void frame(float aspect){float w=width*.88f,h=height*.88f;if(w/h>aspect)w=h*aspect;else h=w/aspect;left=(width-w)/2;top=(height-h)/2;right=left+w;bottom=top+h;}
    void reset(){if(width<=0||height<=0)return;frame(ratio>0?ratio:imageWidth/imageHeight);scale=Math.max(cropWidth()/imageWidth,cropHeight()/imageHeight);imageX=(width-imageWidth*scale)/2;imageY=(height-imageHeight*scale)/2;cover();}
    void setRatio(float ratio){this.ratio=ratio;if(ratio>0)reset();}
    void pan(float dx,float dy){if(scale<=0)return;imageX+=dx;imageY+=dy;cover();}
    void zoom(float factor,float x,float y){
        if(scale<=0||!Float.isFinite(factor)||factor<=0)return;
        float base=Math.max(cropWidth()/imageWidth,cropHeight()/imageHeight);float next=Math.max(base,Math.min(base*12,scale*factor));factor=next/scale;
        imageX=x-(x-imageX)*factor;imageY=y-(y-imageY)*factor;scale=next;cover();
    }
    void corner(int handle,float x,float y){
        boolean west=handle==0||handle==3,north=handle<2;float ax=west?right:left,ay=north?bottom:top;
        float margin=Math.min(width,height)*.025f,maxW=west?ax-margin:width-margin-ax,maxH=north?ay-margin:height-margin-ay;
        float min=Math.min(48,Math.min(maxW,maxH));float w=Math.max(min,west?ax-x:x-ax),h=Math.max(min,north?ay-y:y-ay);
        if(ratio>0){h=Math.max(h,w/ratio);h=Math.min(h,Math.min(maxH,maxW/ratio));w=h*ratio;}else{w=Math.min(w,maxW);h=Math.min(h,maxH);}
        if(w<=0||h<=0)return;
        left=west?ax-w:ax;right=west?ax:ax+w;top=north?ay-h:ay;bottom=north?ay:ay+h;cover();
    }
    void cover(){
        float minimum=Math.max(cropWidth()/imageWidth,cropHeight()/imageHeight);
        if(scale<minimum){float cx=(left+right)/2,cy=(top+bottom)/2,k=minimum/scale;imageX=cx-(cx-imageX)*k;imageY=cy-(cy-imageY)*k;scale=minimum;}
        imageX=Math.max(right-imageWidth*scale,Math.min(left,imageX));imageY=Math.max(bottom-imageHeight*scale,Math.min(top,imageY));
    }
    float[] source(){return new float[]{clamp((left-imageX)/scale,0,imageWidth),clamp((top-imageY)/scale,0,imageHeight),clamp((right-imageX)/scale,0,imageWidth),clamp((bottom-imageY)/scale,0,imageHeight)};}
    void restore(float[] r){
        if(r==null||r.length!=4||!Float.isFinite(r[0]+r[1]+r[2]+r[3])||r[0]<0||r[1]<0||r[2]>imageWidth||r[3]>imageHeight||r[2]<=r[0]||r[3]<=r[1]){reset();return;}
        frame((r[2]-r[0])/(r[3]-r[1]));scale=cropWidth()/(r[2]-r[0]);imageX=left-r[0]*scale;imageY=top-r[1]*scale;cover();
    }
    static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
}
