(function(root){
  'use strict';
  function position(state,elapsed){return Math.max(0,Math.min(state.duration||86400000,state.position+(state.playing?Math.max(0,elapsed):0)));}
  function lineAt(lines,time){var lo=0,hi=lines.length-1,result=-1;while(lo<=hi){var mid=(lo+hi)>>1;if(lines[mid].time<=time){result=mid;lo=mid+1;}else hi=mid-1;}return result;}
  function valid(state){return !!state&&/^[A-Za-z0-9_-]{11}$/.test(state.id)&&typeof state.title==='string'&&typeof state.artist==='string'&&Number.isFinite(state.position)&&state.position>=0&&Number.isFinite(state.duration)&&state.duration>=0&&typeof state.playing==='boolean';}
  function time(ms){var s=Math.floor(Math.max(0,ms)/1000);return Math.floor(s/60)+':'+String(s%60).padStart(2,'0');}
  var api={position:position,lineAt:lineAt,valid:valid,time:time};if(typeof module!=='undefined')module.exports=api;else root.TVCore=api;
})(this);
