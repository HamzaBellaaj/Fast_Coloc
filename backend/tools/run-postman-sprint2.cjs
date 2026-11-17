const {join}=require('node:path');const {writeFileSync}=require('node:fs');const recipe=require('./sprint2-fixtures.cjs');
const newman=require('../target/recipe-tools/node_modules/newman');
(async()=>{try{
 const fixture=await recipe.fixtures();
 const summary=await new Promise((resolve,reject)=>newman.run({collection:require('../postman/FastColoc-Sprint2.postman_collection.json'),environment:{values:[{key:'base_url',value:'http://127.0.0.1:8001',enabled:true},{key:'access_token',value:fixture.owner.session.access_token,enabled:true},{key:'locality_id',value:String(fixture.area.id),enabled:true}]},folder:'Sprint 2 — Annonces',workingDir:join(__dirname,'..'),reporters:'cli',reporter:{cli:{noConsole:true}}},(error,result)=>error?reject(Error('Newman execution failed')):resolve(result)));
 const safe={requests:summary.run.stats.requests.total,assertions:summary.run.stats.assertions.total,failures:summary.run.failures.length};writeFileSync(join(__dirname,'../target/sprint2-postman-report.json'),JSON.stringify(safe,null,2));if(safe.failures)throw Error('Postman assertions failed');console.log(JSON.stringify(safe));
 }finally{console.log('Isolated fixture accounts cleaned:',await recipe.cleanup())}})().catch(e=>{console.error(e.message);process.exitCode=1});
