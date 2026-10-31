const assert=require('node:assert/strict');
const C=require('../app/core.js');
const s={id:'abcdefghijk',title:'Song',artist:'Artist',position:5000,duration:10000,playing:true};
assert(C.valid(s));assert(!C.valid({...s,id:'<script>'}));assert(!C.valid({...s,position:NaN}));
assert.equal(C.position(s,20000),10000);assert.equal(C.position({...s,playing:false},500),5000);
assert.equal(C.lineAt([{time:100,text:'a'},{time:500,text:'b'}],99),-1);
assert.equal(C.lineAt([{time:100},{time:500}],500),1);assert.equal(C.lineAt([],500),-1);
assert.equal(C.time(65000),'1:05');console.log('TV playback timing checks passed');
