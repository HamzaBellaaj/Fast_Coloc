/** Additive, FastColoc-only private bucket provisioning. Never changes an existing bucket. */
const {readFileSync}=require('node:fs');const {join}=require('node:path');
const config=Object.fromEntries(readFileSync(join(__dirname,'../.env.local'),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1).trim()]}));
const base='https://nklsbubnyznlfpxpxsrp.supabase.co';
async function request(method,path,body){const r=await fetch(base+'/storage/v1'+path,{method,headers:{apikey:config.FASTCOLOC_SUPABASE_SECRET_KEY,Authorization:'Bearer '+config.FASTCOLOC_SUPABASE_SECRET_KEY,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});return {status:r.status,data:await r.json()};}
(async()=>{
 let r=await request('GET','/bucket/listing-photos');
 if(r.status===404||r.status===400&&r.data?.message==='Bucket not found'){
  r=await request('POST','/bucket',{id:'listing-photos',name:'listing-photos',public:false,file_size_limit:5242880,allowed_mime_types:['image/jpeg','image/png','image/webp']});
  if(r.status>=300)throw Error('Bucket creation HTTP '+r.status);
  r=await request('GET','/bucket/listing-photos');
 }
 if(r.status!==200||r.data.public!==false||Number(r.data.file_size_limit)!==5242880)throw Error('Unexpected bucket configuration; no existing settings changed');
 console.log(JSON.stringify({bucket:r.data.id,private:r.data.public===false,maxBytes:r.data.file_size_limit,allowedMimeTypes:r.data.allowed_mime_types}));
})().catch(e=>{console.error(e.message);process.exitCode=1});
