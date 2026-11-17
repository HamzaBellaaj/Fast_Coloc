// Reproducible data seed only. Sources: GeoNames export dumps, CC BY 4.0.
// Download countryInfo.txt, admin1CodesASCII.txt and TN.zip into target/geonames,
// extract TN.zip into target/geonames/TN, then run from backend: node tools/prepare-geography.cjs
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const folder=path.resolve(__dirname,'../target/geonames');
const read=f=>fs.readFileSync(path.join(folder,f),'utf8').split(/\r?\n/);
const quote=s=>"'"+s.replace(/'/g,"''")+"'";
const countries=read('countryInfo.txt').filter(s=>s&&!s.startsWith('#')).map(s=>s.split('\t')).filter(s=>/^[A-Z]{2}$/.test(s[0])).map(s=>[s[0],s[4]]);
const admins=read('admin1CodesASCII.txt').filter(s=>s.startsWith('TN.')).map(s=>s.split('\t'));
if(admins.length!==24)throw new Error('Expected 24 Tunisian governorates; review source');
const byCode=new Map(admins.map(a=>[a[0],a[1]]));
const unique=new Map();
for(const line of read('TN/TN.txt')){const a=line.split('\t');if(a[6]==='P'&&['PPLC','PPLA','PPLA2','PPL'].includes(a[7])&&Number(a[14])>0&&byCode.has('TN.'+a[10])){const gov=byCode.get('TN.'+a[10]);unique.set(gov+'\0'+a[1],[gov,a[1]]);}}
let sql='-- GeoNames https://www.geonames.org/export/ — CC BY 4.0, retrieved 2026-11-12.\n-- Data only; names preserved, no generated identifiers hardcoded.\nBEGIN;\n';
sql+='INSERT INTO fastcoloc.countries(code,name) VALUES\n'+countries.map(a=>'('+a.map(quote).join(',')+')').join(',\n')+'\nON CONFLICT(code) DO NOTHING;\n';
sql+='INSERT INTO fastcoloc.governorates(country_code,name) VALUES\n'+admins.map(a=>"('TN',"+quote(a[1])+')').join(',\n')+'\nON CONFLICT(country_code,name) DO NOTHING;\n';
sql+='INSERT INTO fastcoloc.localities(governorate_id,name) SELECT g.id,seed.name FROM (VALUES\n'+[...unique.values()].map(a=>'('+a.map(quote).join(',')+')').join(',\n')+") AS seed(governorate,name) JOIN fastcoloc.governorates g ON g.country_code='TN' AND g.name=seed.governorate\nON CONFLICT(governorate_id,name) DO NOTHING;\nCOMMIT;\n";
fs.writeFileSync(path.resolve(__dirname,'geography.seed.sql'),sql);
const manifest={retrieved:'2026-11-12',license:'CC BY 4.0',attribution:'GeoNames — https://www.geonames.org/',countries:countries.length,governorates:admins.length,localities:unique.size,sources:['https://download.geonames.org/export/dump/countryInfo.txt','https://download.geonames.org/export/dump/admin1CodesASCII.txt','https://download.geonames.org/export/dump/TN.zip'],sha256:{}};
for(const file of ['countryInfo.txt','admin1CodesASCII.txt','TN.zip'])manifest.sha256[file]=crypto.createHash('sha256').update(fs.readFileSync(path.join(folder,file))).digest('hex');
fs.writeFileSync(path.resolve(__dirname,'../../docs/GEOGRAPHY-SOURCE.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({countries:manifest.countries,governorates:manifest.governorates,localities:manifest.localities}));
