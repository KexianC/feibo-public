import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const ts = require('typescript');
const serviceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src/services');
let nextData = true;
const calls = [];
const cache = new Map();
function load(name) {
  if (cache.has(name)) return cache.get(name);
  const source = fs.readFileSync(path.join(serviceRoot, name + '.ts'), 'utf8');
  const js = ts.transpileModule(source, {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
  const module = {exports:{}};
  const sandbox = {module, exports:module.exports, FormData, File, URLSearchParams, console,
    require: (dependency) => dependency === './auth' ? {authenticatedRequest:async (url, options={})=>{calls.push({url,...options}); return {code:200,data:nextData};}} : load(dependency.replace('./',''))};
  vm.runInNewContext(js, sandbox, {filename:name+'.ts'});
  cache.set(name,module.exports);
  return module.exports;
}
const audio = new File(['audio'], 'sample.mp3', {type:'audio/mpeg'});
const video = new File(['video'], 'sample.mp4', {type:'video/mp4'});
const cover = new File(['image'], 'cover.png', {type:'image/png'});
const last = () => calls.at(-1);
const music = load('musicService').default;
await music.create({title:' 新音乐 '}, audio);
assert.equal(last().url,'/api/admin/function/music');
assert.ok(last().body instanceof FormData);
assert.equal(last().body.get('title'),'新音乐');
assert.equal(last().body.get('file').name,'sample.mp3');
assert.equal(last().timeoutMs,120000);
await music.update({id:'123',title:'重命名'});
assert.deepEqual(JSON.parse(last().body),{id:'123',name:'重命名'});
nextData=false;
await assert.rejects(music.update({id:'123',title:'未保存'}),/未完成/);
await assert.rejects(music.delete('123'),/删除未完成/);
nextData=true;
const videos=load('videoService').default;
await assert.rejects(videos.create({title:'测试'},video,undefined),/封面/);
await videos.create({title:'测试'},video,cover);
assert.equal(last().url,'/api/admin/function/video');
assert.equal(last().body.get('coverFile').name,'cover.png');
assert.equal(last().body.get('file').name,'sample.mp4');
const illustrations=load('illustrationService').default;
nextData={id:'12',name:'插图名称',url:'https://example.test/image.png'};
await illustrations.create({title:'插图名称'},cover);
assert.equal(last().url,'/api/admin/function/illustration/add-with-image');
assert.equal((await illustrations.getById('12')).indexImage,'https://example.test/image.png');
assert.equal((await illustrations.getById('12')).title,'插图名称');
const backgrounds=load('backgroundImageService').BackgroundImageService;
nextData={records:[{id:'9223372036854775800',name:'背景',url:'https://example.test/bg.png'}],total:15,pages:3,current:2,size:5};
const page=await backgrounds.getBackgroundImagesPage({page:2,pageSize:5,title:' 测试 ',startDate:'2026-09-01',endDate:'2026-09-09'});
assert.ok(last().url.includes('pageNum=2&pageSize=5'));
assert.ok(last().url.includes('startDate=2026-09-01&endDate=2026-09-09'));
assert.equal(page.records[0].id,'9223372036854775800');
assert.equal(page.records[0].title,'背景');
assert.equal('isShow' in page.records[0],false);
nextData=true;
const model=load('modelService').default;
await model.update({id:'25',title:'模型',sort:7,status:'published'});
assert.deepEqual(JSON.parse(last().body),{id:'25',modelName:'模型',sortNum:7,status:'published'});
const json=JSON.stringify({data:{records:[{id:'25',speakerId:'provider-25',speakerName:'模型'}]}});
await model.uploadJson(new File([json],'models.json',{type:'application/json'}));
assert.equal(last().url,'/api/admin/function/digital-model/upload-json');
assert.equal(last().body,json);
assert.equal(last().timeoutMs,120000);
const count=calls.length;
await assert.rejects(model.uploadJson(new File(['{}'],'bad.json')),/data.records/);
assert.equal(calls.length,count);
const voice=load('voiceService').default;
await voice.create({title:'男声',digitalVoiceId:'9223372036854775800',isDisplay:false});
assert.deepEqual(JSON.parse(last().body),{name:'男声',digitalVoiceId:'9223372036854775800',isDisplay:false});
await assert.rejects(voice.create({title:'男声',digitalVoiceId:'https://audio.test/file.mp3'}),/供应商语音 ID/);
await voice.update({id:'18',title:' 新女声 ',digitalVoiceId:'9223372036854775800',styleName:' 温和 ',gender:2,isDisplay:false,userId:'999',isDelete:true});
assert.equal(last().url,'/api/admin/function/voice');
assert.equal(last().method,'PUT');
assert.deepEqual(JSON.parse(last().body),{id:'18',name:'新女声',digitalVoiceId:'9223372036854775800',styleName:'温和',gender:2,isDisplay:false});
await voice.update({id:'18',title:'新女声',digitalVoiceId:'9223372036854775800',isDisplay:true});
assert.deepEqual(JSON.parse(last().body),{id:'18',name:'新女声',digitalVoiceId:'9223372036854775800',styleName:'',gender:0,isDisplay:true});
const voiceCalls=calls.length;
await assert.rejects(voice.update({id:'18',title:'女声',digitalVoiceId:'9223372036854775808',isDisplay:false}),/供应商语音 ID/);
await assert.rejects(voice.update({id:'not-an-id',title:'女声',digitalVoiceId:'123',isDisplay:false}),/记录 ID/);
assert.equal(calls.length,voiceCalls);
for (const failure of [false,null,undefined]) {
  nextData=failure;
  await assert.rejects(voice.update({id:'18',title:'女声',digitalVoiceId:'123',isDisplay:false}),/未完成/);
}
nextData={id:18,name:'新女声',digitalVoiceId:'9223372036854775800',styleName:'温和',gender:2,isDisplay:false};
const savedVoice=await voice.getById('18');
assert.equal(savedVoice.id,'18');
assert.equal(savedVoice.digitalVoiceId,'9223372036854775800');
assert.equal(savedVoice.isDisplay,false);
assert.equal(savedVoice.gender,2);
nextData=true;
const cases=load('caseVideoService').default;
await cases.create({title:'案例',scene:'临床',industry:'医疗',sort:2,isShow:0},video,cover);
assert.equal(last().url,'/api/admin/function/case-video/add-with-files');
assert.equal(last().body.get('videoFile').name,'sample.mp4');
assert.equal(last().body.get('coverImage').name,'cover.png');
assert.equal(last().body.get('isShow'),'0');
await cases.update({id:'44',title:'更新案例',scene:'',industry:'医学',sort:3,isShow:1},undefined,cover);
assert.equal(last().url,'/api/admin/function/case-video/update-with-files');
assert.equal(last().body.get('id'),'44');
assert.equal(last().body.has('videoFile'),false);
assert.equal(last().body.get('coverImage').name,'cover.png');
for (const [remove,url] of [
  [()=>music.delete('12'),'/api/admin/function/music/12'],
  [()=>videos.delete('12'),'/api/admin/function/video/12'],
  [()=>illustrations.delete('12'),'/api/admin/function/illustration/12'],
  [()=>backgrounds.deleteBackgroundImage('12'),'/api/admin/function/background-image/12'],
  [()=>model.delete('12'),'/api/admin/function/digital-model/12'],
  [()=>voice.delete('12'),'/api/admin/function/voice/12'],
  [()=>cases.delete('12'),'/api/admin/function/case-video/12']
]) {
  nextData=true;
  await remove();
  assert.equal(last().method,'DELETE');
  assert.equal(last().url,url);
  for (const failure of [false,null]) {
    nextData=failure;
    await assert.rejects(remove(),/删除未完成/);
  }
}
nextData={records:[],total:0,pages:0,current:1,size:10};
await voice.getPage({page:1,size:10,title:'语音'});
assert.ok(last().url.includes('name='));
assert.ok(!last().url.includes('title='));
await model.getPage({page:1,size:10,title:'模型'});
assert.ok(last().url.includes('name='));
console.log('Material contract checks passed: 7 services, multipart file keys, DTO mapping, voice edits, all DELETE responses, false/null-result rejection, model import, long IDs, pagination and dates.');
