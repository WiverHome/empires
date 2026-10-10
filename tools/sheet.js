// upscale a packed 16px sheet with index digits drawn beneath each tile
const png=require('./png');const [,,inp,out,Z=4]=process.argv;const im=png.read(inp);const cols=im.w/16,rows=im.h/16,z=+Z,cell=16*z,lab=9,P=cell+lab+2;
const W=cols*P,H=rows*P,d=Buffer.alloc(W*H*4);for(let i=0;i<W*H;i++){d[i*4]=60;d[i*4+1]=60;d[i*4+2]=60;d[i*4+3]=255;}
const F={0:'111101101101111',1:'010110010010111',2:'111001111100111',3:'111001111001111',4:'101101111001001',5:'111100111001111',6:'111100111101111',7:'111001001001001',8:'111101111101111',9:'111101111001111'};
function px(x,y,r,g,b){if(x<0||y<0||x>=W||y>=H)return;const o=(y*W+x)*4;d[o]=r;d[o+1]=g;d[o+2]=b;d[o+3]=255;}
for(let t=0;t<cols*rows;t++){const tx=t%cols,ty=(t/cols)|0,ox=tx*P,oy=ty*P;
for(let y=0;y<cell;y++)for(let x=0;x<cell;x++){const sx=tx*16+((x/z)|0),sy=ty*16+((y/z)|0),so=(sy*im.w+sx)*4;const a=im.data[so+3]/255;const bg=((x>>3)+(y>>3))&1?110:130;
px(ox+x,oy+y,Math.round(im.data[so]*a+bg*(1-a)),Math.round(im.data[so+1]*a+bg*(1-a)),Math.round(im.data[so+2]*a+bg*(1-a)));}
String(t).split('').forEach((ch,k)=>{const f=F[ch];for(let i=0;i<15;i++)if(f[i]==='1'){const X=ox+2+k*8+(i%3)*2,Y=oy+cell+1+((i/3)|0)*2;px(X,Y,255,255,0);px(X+1,Y,255,255,0);px(X,Y+1,255,255,0);px(X+1,Y+1,255,255,0);}});}
png.write(out,{w:W,h:H,data:d});
