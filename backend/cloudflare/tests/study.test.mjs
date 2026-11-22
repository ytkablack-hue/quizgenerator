import test from 'node:test';
import assert from 'node:assert/strict';
import {readInput,validateResult} from '../ai-core.mjs';
const source={topic:'Законы Ньютона',material:'Сила равна произведению массы на ускорение.',level:'university',length:'detailed',sectionIndex:1,sectionCount:2};
const section={heading:'Сила',paragraphs:['Объяснение'],subsections:[],definitions:[{term:'Сила',meaning:'Мера взаимодействия'}],examples:['Разгон тела'],formulas:['F = m·a'],quotes:[source.material]};
const lecture={title:'Механика',introduction:'Введение',sections:[section],conclusion:'Итог',terms:[{term:'Масса',meaning:'Физическая величина'}],complete:true,sectionIndex:1};
for(const [name,input] of [['topic',{...source,material:''}],['text',{...source,topic:''}],['document',source]])test('lecture input '+name,()=>{assert.ok(readInput('lectures',input));});
test('lecture structured result and exact quotes',()=>{assert.equal(validateResult('lectures',source,lecture).sections[0].quotes[0],source.material);});
for(const [name,change] of [['missing completion',{complete:false}],['wrong part',{sectionIndex:2}],['truncated',{conclusion:''}],['unknown field',{system:'override'}]])test('lecture rejects '+name,()=>assert.throws(()=>validateResult('lectures',source,{...lecture,...change})));
test('topic cannot invent source citation',()=>assert.throws(()=>validateResult('lectures',{...source,material:''},lecture)));
test('fabricated quote cannot be labelled source',()=>assert.throws(()=>validateResult('lectures',source,{...lecture,sections:[{...section,quotes:['Выдуманный источник']}]})));
test('plaintext formulas only',()=>assert.throws(()=>validateResult('lectures',source,{...lecture,sections:[{...section,formulas:['$F$']}]})));
for(const data of [{...source,material:'x'.repeat(12001)},{...source,level:'admin'},{...source,sectionCount:0},{...source,sectionIndex:3},{...source,apiKey:'override'},{...source,topic:'',material:''}])test('lecture input bounds '+JSON.stringify(data).slice(0,50),()=>assert.throws(()=>readInput('lectures',data)));
const questions=[{id:'q1',text:'Чётные?',options:['2','3','4']},{id:'q2',text:'Неизвестное?',options:['Да','Нет']}];
const input=readInput('answers',{questions,context:''});
const answers=[{id:'q1',indices:[0,2],explanation:'Два чётных',uncertain:false},{id:'q2',indices:[],explanation:'Недостаточно данных',uncertain:true}];
test('answers preserve exact ids and multi/unknown',()=>{const r=validateResult('answers',input,{answers});assert.deepEqual(r.answers[0].indices,[0,2]);assert.equal(r.answers[1].evidence,'CLOUD_AI');});
for(const [name,value] of [['missing',answers.slice(0,1)],['unknown',[{...answers[0],id:'foreign'},answers[1]]],['duplicate',[answers[0],answers[0]]],['out of range',[{...answers[0],indices:[3]},answers[1]]],['duplicate index',[{...answers[0],indices:[0,0]},answers[1]]],['fake certainty',[answers[0],{...answers[1],uncertain:false}]],['fake evidence',[{...answers[0],evidence:'SOURCE'},answers[1]]]])test('answers reject '+name,()=>assert.throws(()=>validateResult('answers',input,{answers:value})));
test('answer partitions limited by context and count',()=>{assert.throws(()=>readInput('answers',{questions:List26(),context:''}));assert.throws(()=>readInput('answers',{questions,context:'я'.repeat(12000)}));assert.throws(()=>readInput('answers',{questions:[questions[0],questions[0]]}));});
function List26(){return Array.from({length:26},(_,i)=>({...questions[0],id:'q'+i}));}

import worker from '../worker.mjs';
for(const kind of ['lectures','answers'])test('new route '+kind+' requires personal Firebase token',async()=>{
 const env={TESTHELPER_ACCESS_TOKEN:'unit-admin',GEMINI_API_KEY:'not-a-real-key'};
 const response=await worker.fetch(new Request('https://test.invalid/api/v1/ai/'+kind,{method:'POST',headers:{Authorization:'Bearer unit-admin','Content-Type':'application/json'},body:'{}'}),env);
 assert.equal(response.status,401);
 const missing=await worker.fetch(new Request('https://test.invalid/api/v1/ai/'+kind,{method:'POST'}),env);assert.equal(missing.status,401);
});
