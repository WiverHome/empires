const fs=require('fs'),zlib=require('zlib');
function decode(buf){let p=8,ihdr,plte,trns,idat=[];while(p<buf.length){const len=buf.readUInt32BE(p),type=buf.toString('ascii',p+4,p+8),d=buf.slice(p+8,p+8+len);p+=12+len;
if(type==='IHDR')ihdr={w:d.readUInt32BE(0),h:d.readUInt32BE(4),bd:d[8],ct:d[9],il:d[12]};else if(type==='PLTE')plte=d;else if(type==='tRNS')trns=d;else if(type==='IDAT')idat.push(d);}
const {w,h,bd,ct}=ihdr;if(bd!==8)throw new Error('bd '+bd);const ch={0:1,2:3,3:1,4:2,6:4}[ct];const raw=zlib.inflateSync(Buffer.concat(idat));const stride=w*ch;const out=Buffer.alloc(w*h*4);let prev=Buffer.alloc(stride);
for(let y=0;y<h;y++){const f=raw[y*(stride+1)],line=Buffer.from(raw.slice(y*(stride+1)+1,(y+1)*(stride+1)));for(let i=0;i<stride;i++){const a=i>=ch?line[i-ch]:0,b=prev[i],c=i>=ch?prev[i-ch]:0;let v=line[i];
if(f===1)v+=a;else if(f===2)v+=b;else if(f===3)v+=(a+b)>>1;else if(f===4){const pp=a+b-c,pa=Math.abs(pp-a),pb=Math.abs(pp-b),pc=Math.abs(pp-c);v+=(pa<=pb&&pa<=pc)?a:(pb<=pc?b:c);}line[i]=v&255;}
for(let x=0;x<w;x++){const o=(y*w+x)*4;if(ct===3){const k=line[x];out[o]=plte[k*3];out[o+1]=plte[k*3+1];out[o+2]=plte[k*3+2];out[o+3]=trns&&k<trns.length?trns[k]:255;}
else if(ct===6){line.copy(out,o,x*4,x*4+4);}else if(ct===2){out[o]=line[x*3];out[o+1]=line[x*3+1];out[o+2]=line[x*3+2];out[o+3]=255;}else{const g=line[x*ch];out[o]=out[o+1]=out[o+2]=g;out[o+3]=ct===4?line[x*2+1]:255;}}prev=line;}
return {w,h,data:out};}
const crcT=new Int32Array(256).map((_,n)=>{let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;return c;});
function crc(b){let c=-1;for(const x of b)c=crcT[(c^x)&255]^(c>>>8);return (c^-1)>>>0;}
function chunk(t,d){const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const td=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc(td));return Buffer.concat([l,td,c]);}
function encode({w,h,data}){const raw=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++){raw[y*(w*4+1)]=0;data.copy(raw,y*(w*4+1)+1,y*w*4,(y+1)*w*4);}
const ih=Buffer.alloc(13);ih.writeUInt32BE(w,0);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=6;return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
module.exports={decode,encode,read:f=>decode(fs.readFileSync(f)),write:(f,img)=>fs.writeFileSync(f,encode(img))};
