const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/deeplist-wasted-demo.html', 'utf8');
class Element {
  constructor() {
    this.children = []; this.listeners = {}; this.properties = {}; this.classes = new Set();
    this.style = {setProperty: (key, value) => this.properties[key] = value};
    this.classList = {add: key => this.classes.add(key), remove: key => this.classes.delete(key),
      toggle: (key, enabled) => enabled ? this.classes.add(key) : this.classes.delete(key)};
    this.offsetTop = 0; this.offsetHeight = 50; this.clientHeight = 350;
  }
  append(child) { child.offsetTop = this.children.length * 80; this.children.push(child); }
  setAttribute() {} removeAttribute() {}
  addEventListener(name, handler) { (this.listeners[name] ??= []).push(handler); }
  dispatch(name, event) { for (const fn of this.listeners[name] || []) fn(event); }
}
const elements = {}, document = new Element();
document.hidden = false;
document.getElementById = id => elements[id] ??= new Element();
document.createElement = () => new Element();
const audio = document.getElementById('appleDemoAudio');
Object.assign(audio, {currentTime: 0, duration: 26.536735, paused: true, ended: false,
  pause() { this.paused = true; this.dispatch('pause'); }});
let nativePauses = 0;
const window = {DeeplistDemo: {onPlay() { nativePauses++; }}};
const context = vm.createContext({document, window, console, requestAnimationFrame: () => 1, cancelAnimationFrame() {},
  fetch() { throw Error('Bundled lyrics must not require a network request'); }});
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(match => match[1]);
for (const script of scripts) vm.runInContext(script, context);
(async () => {
  await new Promise(setImmediate);
  const stack = elements.appleDemoLines;
  assert.ok(stack.children.length > 5, 'Bundled timed lyric rows should render');
  const first = stack.children[0];
  assert.ok(first.classes.has('am-active'));
  audio.paused = false; audio.dispatch('play');
  assert.equal(nativePauses, 1, 'Demo playback must request native-player pause');
  assert.ok(elements.appleLyricsDemo.classes.has('am-playing'));
  audio.currentTime = 0.12; audio.dispatch('seeked');
  assert.equal(first.children[0].properties['--fill'], '50%');
  assert.ok(first.children[0].classes.has('am-hot'), 'Active word should lift and highlight');
  stack.children[3].onclick();
  assert.equal(audio.currentTime, 9, 'Tapping a line should seek to its own timestamp');
  assert.ok(stack.children[3].classes.has('am-active'));
  assert.match(stack.style.transform, /^translateY\(-/);
  elements.amSize.dispatch('input', {target: {value: '64'}});
  assert.equal(elements.appleLyricsDemo.properties['--am-size'], '64px');
  elements.amSpacing.dispatch('input', {target: {value: '0.2'}});
  assert.equal(elements.appleLyricsDemo.properties['--am-gap'], '0.2em');
  elements.amBlur.dispatch('input', {target: {value: '60'}});
  assert.equal(elements.appleLyricsDemo.properties['--am-blur'], '60px');
  elements.amMotion.dispatch('change', {target: {checked: false}});
  assert.ok(elements.appleLyricsDemo.classes.has('am-still'));
  document.hidden = true; document.dispatch('visibilitychange');
  assert.ok(audio.paused, 'Backgrounding must stop demo audio');
  assert.ok(!elements.appleLyricsDemo.classes.has('am-playing'));
  assert.match(html, /media-src https:\/\/world\.damndeepak\.workers\.dev/);
  console.log('PASS: bundled Wasted rows, timed word fill, tap-to-seek, scroll, all controls, native pause handoff and background stop');
})().catch(error => { console.error(error); process.exitCode = 1; });
