const state = {
  mode: "standby", tab: "scenes", identity: "known", source: "voice",
  gas: false, nebulizing: false, paused: false, fault: null,
  countdown: 60, collectionStep: 0, profile: {}, spoken: "", pending: null,
  logs: [], broadcastReturn: "awake"
};

const faults = {
  network: { title: "网络连接失败", voice: "网络连接失败，暂时无法使用，请检查网络后重试。", steps: ["检查 Wi-Fi 或 4G 网络连接。", "确认网络恢复后重新尝试。", "如仍有异常，请联系人工客服。"] },
  unlock: { title: "设备尚未解锁", voice: "设备尚未解锁，请先完成解锁。", steps: ["请按设备操作说明完成解锁。", "如无法解锁，请联系管理员或人工客服。"] },
  power: { title: "电源通讯故障", voice: "设备电源通讯异常，请检查电源后重启。", steps: ["检查设备电源和连接线。", "重启设备后观察状态。", "故障仍存在时联系人工客服。"] },
  serial: { title: "设备连接异常", voice: "与设备连接异常，请稍候或检查设备。", steps: ["检查主控与上位机连接。", "等待串口自动重连。", "持续异常时联系人工客服。"] }
};

const commands = [
  ["start-gas", "开始产气"], ["stop-gas", "停止产气"],
  ["start-neb", "开始雾化"], ["stop-neb", "停止雾化"],
  ["pause-gas", "暂停产气"], ["resume-gas", "恢复产气"],
  ["sleep", "睡眠模式"]
];

const $ = id => document.getElementById(id);
const esc = value => String(value ?? "").replace(/[&<>"']/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[char]);

function log(message) {
  state.logs.unshift({time: new Date().toLocaleTimeString("zh-CN", {hour12:false}), message});
  state.logs = state.logs.slice(0, 80);
  renderConsole();
}

function speak(text) {
  state.spoken = text;
  log(`语音反馈（模拟）：${text}`);
}

function closeConsole() {
  $("console-panel").classList.remove("open");
  $("console-panel").setAttribute("aria-hidden", "true");
  $("console-toggle").setAttribute("aria-expanded", "false");
  $("scrim").hidden = true;
}

function openConsole() {
  $("console-panel").classList.add("open");
  $("console-panel").setAttribute("aria-hidden", "false");
  $("console-toggle").setAttribute("aria-expanded", "true");
  $("scrim").hidden = false;
  renderConsole();
}

function visualMode() {
  if (state.fault) return "fault";
  if (state.mode === "sleep" || state.mode === "collect" || state.mode === "broadcast") return state.mode;
  return state.gas ? "working" : state.mode;
}

function render() {
  const mode = visualMode();
  $("sleep-screen").hidden = mode !== "sleep";
  const content = {
    standby: ["随时为您服务", "您好，我在这里", "说出“潓美医疗”，即可开始对话。", "安静待命"],
    awake: ["我正在倾听", state.identity === "known" ? "王爷爷，您好" : "您好，很高兴认识您", state.identity === "known" ? "请告诉我，您想让设备做什么？" : "准备好后，您可以说“开始产气”。", "正在倾听"],
    working: [state.paused ? "产气已暂停" : "设备运行中", state.paused ? "已为您暂停产气" : "正在为您产气", state.paused ? "需要继续时，请说“恢复产气”。" : "请安心使用，我会留意设备状态。", state.paused ? "等待恢复" : "运行中"],
    fault: ["设备需要检查", faults[state.fault]?.title || "", faults[state.fault]?.voice || "", "故障提示"],
    collect: ["设备运行中 · 新用户引导", "我们先认识一下", "产气已开始。我会一次只问您一个问题。", "一问一答"],
    broadcast: ["设备主动提醒", "请留意设备提示", state.spoken, "正在播报"],
    sleep: ["低功耗待机", "设备已进入睡眠", "说“潓美医疗”即可唤醒。", "睡眠中"]
  }[mode];
  $("view-eyebrow").textContent = content[0];
  $("view-title").textContent = content[1];
  $("view-lead").textContent = content[2];
  $("avatar-state").lastChild.textContent = content[3];
  $("top-status").textContent = mode === "fault" ? "设备故障" : mode === "standby" ? "待机中" : mode === "sleep" ? "睡眠中" : state.paused ? "产气暂停" : state.gas ? "产气中" : "交互中";
  $("avatar-frame").style.filter = mode === "fault" ? "saturate(.68)" : "none";
  const detail = $("view-detail");
  const actions = $("view-actions");
  detail.innerHTML = ""; actions.innerHTML = "";

  if (mode === "standby") {
    detail.innerHTML = '<div class="status-line"><span class="status-pill">语音唤醒词：潓美医疗</span><span class="status-pill">人脸驻留 3 秒</span></div>';
    actions.innerHTML = '<button class="primary-action" type="button" data-action="wake-voice">体验语音唤醒</button>';
  } else if (mode === "awake") {
    detail.innerHTML = `<div class="voice-card"><span class="wave"><i></i><i></i><i></i></span>${esc(state.spoken || "我在，请说。")} <small>· 模拟语音</small></div>`;
    actions.innerHTML = '<button class="primary-action" type="button" data-action="show-commands">查看可说的命令</button>';
  } else if (mode === "working") {
    detail.innerHTML = `<div class="progress-card"><div><small>当前状态 · 来自模拟 MCU</small><strong>${state.paused ? "产气暂停" : state.nebulizing ? "产气与雾化进行中" : "产气进行中"}</strong></div><span class="pulse" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span></div><div class="status-line" style="margin-top:10px"><span class="status-pill active">产气 ${state.paused ? "暂停" : "开启"}</span><span class="status-pill ${state.nebulizing ? "active" : ""}">雾化 ${state.nebulizing ? "开启" : "关闭"}</span></div>`;
    actions.innerHTML = state.paused ? '<button class="primary-action" type="button" data-command="resume-gas">恢复产气</button>' : '<button class="secondary-action" type="button" data-command="pause-gas">暂停产气</button>';
  } else if (mode === "fault") {
    const fault = faults[state.fault];
    detail.innerHTML = `<div class="fault-card"><strong>排查指引</strong><ol>${fault.steps.map(step => `<li>${esc(step)}</li>`).join("")}</ol></div>`;
    actions.innerHTML = '<button class="primary-action" type="button" data-action="service">联系客服</button><button class="secondary-action" type="button" data-action="recover-fault">模拟故障解除</button>';
  } else if (mode === "collect") {
    renderCollection(detail, actions);
  } else if (mode === "broadcast") {
    detail.innerHTML = `<div class="voice-card"><span class="wave"><i></i><i></i><i></i></span>${esc(state.spoken)} <small>· 模拟语音与动画同步</small></div>`;
    actions.innerHTML = '<button class="primary-action" type="button" data-action="interrupt">模拟用户插话</button><button class="secondary-action" type="button" data-action="end-broadcast">播报结束</button>';
  }
  $("footer-primary").textContent = mode === "standby" ? "本地待机 · 不保存环境音频" : mode === "fault" ? "故障期间设备命令已拦截" : mode === "sleep" ? "低功耗唤醒通路模拟" : "本地交互 · MCU 状态模拟";
  $("footer-secondary").textContent = mode === "fault" ? "故障码与客服渠道待硬件 / 售后确认" : mode === "collect" ? "采集不中断产气" : "演示内容不连接真实设备";
  renderConsole();
}

function renderCollection(detail, actions) {
  const step = state.collectionStep;
  const prompts = [
    ["称谓", "我应该怎么称呼您？", '<button type="button" data-answer="appellation:爷爷">爷爷</button><button type="button" data-answer="appellation:奶奶">奶奶</button><button type="button" data-answer="appellation:先生">先生</button><button type="button" data-answer="appellation:女士">女士</button>'],
    ["年龄", "请问您今年多大年纪？", '<input id="age-input" type="number" inputmode="numeric" min="1" max="120" placeholder="输入年龄" aria-label="年龄"><button type="button" data-action="submit-age">确认年龄</button>'],
    ["健康信息授权", "疾病信息属于敏感资料，您愿意提供吗？", '<button type="button" data-answer="consent:yes">愿意提供</button><button type="button" data-answer="consent:no">暂不提供</button>'],
    ["疾病信息", "您目前主要关注哪方面的呼吸健康？", '<button type="button" data-answer="condition:慢阻肺">慢阻肺</button><button type="button" data-answer="condition:哮喘">哮喘</button><button type="button" data-answer="condition:支气管炎">支气管炎</button><button type="button" data-answer="condition:暂不回答">暂不回答</button>'],
    ["复述确认", `我会称呼您${esc(state.profile.appellation || "用户")}，${esc(state.profile.age || "年龄待补录")}，健康信息${esc(state.profile.condition || "待补录")}。信息正确吗？`, '<button type="button" data-action="confirm-profile">确认并完成</button><button type="button" data-action="restart-profile">重新填写</button>']
  ];
  const [label, question, controls] = prompts[step];
  detail.innerHTML = `<div class="collection-card"><div class="collection-step">${step + 1} / 5 · ${label}</div><p>${question}</p><div class="choice-row">${controls}</div><small>仅模拟信息采集，不保存任何个人资料</small></div>`;
  actions.innerHTML = '<button class="secondary-action" type="button" data-action="cancel-profile">先不用了</button>';
}

function wake(source) {
  state.source = source;
  state.mode = "awake";
  if (state.fault) {
    state.mode = "fault";
    speak(faults[state.fault].voice);
    log(`${source === "voice" ? "唤醒词" : "人脸驻留 3 秒"}命中；故障检查阻断命令`);
  } else {
    const greeting = state.identity === "known" ? "王爷爷，您好。我在，请说。" : "您好。我在，请说。您可以先开始产气。";
    speak(greeting);
    log(`${source === "voice" ? "唤醒词" : "人脸驻留 3 秒"}命中；${state.identity === "known" ? "已有用户关联" : "新用户待采集"}（模拟）`);
  }
  render();
}

function setFault(kind) {
  state.fault = kind;
  state.mode = "fault";
  speak(faults[kind].voice);
  log(`MCU 故障上报（模拟，码值 TBD）：${faults[kind].title}；命令阻断`);
  render();
}

function recoverFault() {
  if (!state.fault) return;
  log(`MCU 故障解除（模拟，码值 TBD）：${faults[state.fault].title}`);
  state.fault = null;
  state.mode = state.gas ? "working" : "awake";
  speak("设备状态已恢复，可以继续使用。");
  render();
}

function reject(text) {
  speak(text);
  log(`前置状态校验未通过；无报文下发：${text}`);
  render();
}

function requestCommand(command) {
  if (state.mode === "standby" && command !== "sleep") return reject("请先唤醒我，再说出需要的操作。");
  if (state.mode === "sleep") return;
  if (state.fault && command !== "sleep") return reject("设备存在故障，暂时不能执行该操作。");
  if (command === "stop-gas" && !state.gas) return reject("当前未在产气，无需停止。");
  if (command === "start-neb" && (!state.gas || state.paused)) return reject("需要先开始产气，才能雾化。需要我现在开始产气吗？");
  if (command === "stop-neb" && !state.nebulizing) return reject("当前未在雾化，无需停止。");
  if (command === "pause-gas" && (!state.gas || state.paused)) return reject("当前未在产气，无法暂停。");
  if (command === "resume-gas" && !state.paused) return reject("当前未处于暂停，无需恢复。");
  if (command === "stop-gas" || command === "sleep") {
    state.pending = command;
    $("modal-title").textContent = command === "stop-gas" ? "确认停止产气？" : "确认进入睡眠？";
    $("modal-message").textContent = command === "stop-gas" ? "停止后，本次产气和雾化都会结束。" : "进入低功耗待机后，可以通过唤醒词或人脸驻留重新唤醒。";
    $("modal-backdrop").hidden = false;
    log(`高风险命令待二次确认：${command === "stop-gas" ? "停止产气" : "睡眠模式"}`);
    return;
  }
  execute(command);
}

function execute(command) {
  const wasGas = state.gas;
  const name = commands.find(row => row[0] === command)?.[1] || command;
  log(`命令校验通过：${name}；模拟串口下发（完整协议 TBD）`);
  if (command === "start-gas") {state.gas = true; state.paused = false; state.countdown = 60; state.mode = "working"; speak(wasGas ? "已为您重新开始产气。" : "好的，已为您开始产气。");}
  if (command === "stop-gas") {state.gas = false; state.nebulizing = false; state.paused = false; state.mode = "awake"; speak("已为您停止产气。");}
  if (command === "start-neb") {state.nebulizing = true; state.mode = "working"; speak("雾化已开始。");}
  if (command === "stop-neb") {state.nebulizing = false; state.mode = "working"; speak("雾化已停止，产气仍在继续。");}
  if (command === "pause-gas") {state.paused = true; state.mode = "working"; speak("已为您暂停产气。");}
  if (command === "resume-gas") {state.paused = false; state.mode = "working"; speak("已为您恢复产气。");}
  if (command === "sleep") {state.mode = "sleep"; speak("即将进入睡眠，您可以再说潓美医疗唤醒我。");}
  log(`MCU 应答成功（模拟，码值 TBD）：${name}`);
  if (command === "start-gas" && state.identity === "new" && !wasGas) {state.mode = "collect"; state.collectionStep = 0; state.profile = {}; log("新用户产气启动后，开始一问一答采集");}
  render();
}

function passive(text, label) {
  if (state.fault) return;
  state.broadcastReturn = state.gas ? "working" : "awake";
  state.mode = "broadcast";
  speak(text);
  log(`MCU 被动上报（模拟，${label}码值 TBD）；同码 10 秒去重为需求示意`);
  render();
}

function finishBroadcast(interrupted) {
  state.mode = state.broadcastReturn;
  log(interrupted ? "用户插话：模拟语音及提示动画同步停止，转入倾听" : "模拟语音及提示动画同步结束");
  state.spoken = interrupted ? "我在，请说。" : "";
  render();
}

function renderConsole() {
  const el = $("console-body");
  if (!el) return;
  document.querySelectorAll(".console-tabs button").forEach(button => button.classList.toggle("active", button.dataset.tab === state.tab));
  if (state.tab === "scenes") {
    el.innerHTML = `<h3>身份样本</h3><div class="radio-row"><button type="button" class="${state.identity === "known" ? "selected" : ""}" data-identity="known">已有用户 · 王爷爷</button><button type="button" class="${state.identity === "new" ? "selected" : ""}" data-identity="new">新用户</button></div><h3>唤醒路径</h3><div class="action-grid"><button type="button" data-action="wake-voice">说出“潓美医疗”</button><button type="button" data-action="wake-face">人脸驻留 3 秒</button></div><p class="minor">语音、声纹、人脸及活体检测均为模拟。</p><h3>故障注入</h3><div class="action-grid"><button type="button" class="danger" data-fault="network">网络连接失败</button><button type="button" class="danger" data-fault="unlock">设备未解锁</button><button type="button" class="danger" data-fault="power">电源通讯故障</button><button type="button" class="danger" data-fault="serial">串口通讯中断</button></div><button type="button" class="full-button" data-action="recover-fault">模拟故障解除</button><h3>会话</h3><div class="action-grid"><button type="button" data-action="reset">回到待机</button><button type="button" data-action="nudge">15 秒无输入提醒</button></div><p class="summary">当前：${state.fault ? faults[state.fault].title : state.paused ? "暂停产气" : state.gas ? state.nebulizing ? "产气 + 雾化" : "产气中" : state.mode === "sleep" ? "睡眠" : "未产气"}。所有状态和报文均为前端模拟。</p>`;
  } else if (state.tab === "commands") {
    el.innerHTML = `<h3>标准命令</h3><div class="action-grid">${commands.map(([id, label]) => `<button type="button" data-command="${id}">${label}</button>`).join("")}</div><p class="minor">此处模拟自然语言已解析为标准命令；真实语音输入尚未接入。</p><h3>条件测试</h3><button type="button" class="full-button" data-action="ambiguous-stop">只说“停” · 请求澄清</button><button type="button" class="full-button" data-action="timeout">模拟 5 秒无应答 / 最多重发 2 次</button><p class="summary">停止产气、睡眠模式需要二次确认。不可执行时仅提示，不模拟下发。</p>`;
  } else if (state.tab === "reports") {
    el.innerHTML = `<h3>MCU 被动上报</h3><button type="button" class="full-button" data-report="gas-done">产气完成</button><button type="button" class="full-button" data-report="neb-done">雾化完成</button><button type="button" class="full-button" data-report="gas-remain">剩余时间提醒</button><button type="button" class="full-button" data-report="success">命令执行成功应答</button><button type="button" class="full-button" data-report="failed">命令执行失败应答</button><h3>模拟说明</h3><p class="summary">真实通道应每 500 ms 上报一帧；这里仅模拟事件结果。故障提示高于普通播报，用户插话可终止当前提示。应答与故障码值待硬件确认。</p>`;
  } else {
    el.innerHTML = `<h3>本次会话</h3><div class="log-list">${state.logs.length ? state.logs.map(row => `<div class="log-item"><time>${esc(row.time)}</time>${esc(row.message)}</div>`).join("") : '<p>暂无事件。操作上方场景或命令后，这里会记录模拟结果。</p>'}</div>`;
  }
}

function handleAction(action) {
  if (action === "wake-voice") wake("voice");
  else if (action === "wake-face") wake("face");
  else if (action === "show-commands") {state.tab = "commands"; openConsole();}
  else if (action === "recover-fault") recoverFault();
  else if (action === "service") {log("客服渠道待售后团队确认；本原型不展示虚构电话或二维码"); alert("客服渠道待确认（TBD）。当前为交互原型，没有真实报修入口。");}
  else if (action === "cancel-modal") {state.pending = null; $("modal-backdrop").hidden = true; log("用户取消高风险命令");}
  else if (action === "confirm-modal") {const command = state.pending; state.pending = null; $("modal-backdrop").hidden = true; if (command) execute(command);}
  else if (action === "reset") {Object.assign(state,{mode:"standby",gas:false,nebulizing:false,paused:false,fault:null,spoken:"",pending:null,profile:{},collectionStep:0}); log("演示状态复位至待机"); render();}
  else if (action === "nudge") {speak("您还需要我帮忙吗？"); log("唤醒后 15 秒无有效输入，主动询问一次（手动模拟）"); render();}
  else if (action === "ambiguous-stop") {speak("您要停止产气，还是停止雾化？"); log("命令意图不明确：只说‘停’，未下发"); render();}
  else if (action === "timeout") {speak("设备暂无响应，请稍候或检查设备。"); log("模拟 5 秒无应答，重发 2 次后失败；真实串口未接入"); render();}
  else if (action === "interrupt") finishBroadcast(true);
  else if (action === "end-broadcast") finishBroadcast(false);
  else if (action === "submit-age") {const value = Number($("age-input")?.value); if (Number.isInteger(value) && value >= 1 && value <= 120) {state.profile.age = value; state.collectionStep = 2; log("新用户年龄已记录（仅页面内存）"); render();} else alert("请输入 1 至 120 之间的年龄。")}
  else if (action === "confirm-profile") {log("新用户复述确认完成；仅模拟落库，未保存生物特征"); state.identity = "known"; state.mode = "working"; speak("谢谢，已经记下了。产气正在继续。"); render();}
  else if (action === "restart-profile") {state.collectionStep = 0; state.profile = {}; render();}
  else if (action === "cancel-profile") {log("用户终止采集；已填资料仅保留在当前页面内存，产气继续"); state.mode = "working"; speak("好的，先不填写，产气继续。"); render();}
}

document.addEventListener("click", event => {
  const target = event.target.closest("[data-action],[data-command],[data-answer],[data-fault],[data-report],[data-tab],[data-identity]");
  if (!target) return;
  if (target.dataset.tab) {state.tab = target.dataset.tab; renderConsole();}
  else if (target.dataset.identity) {state.identity = target.dataset.identity; log(`演示身份切换：${state.identity === "known" ? "已有用户" : "新用户"}`); render();}
  else if (target.dataset.command) requestCommand(target.dataset.command);
  else if (target.dataset.fault) setFault(target.dataset.fault);
  else if (target.dataset.action) handleAction(target.dataset.action);
  else if (target.dataset.answer) {
    const [key,value] = target.dataset.answer.split(":");
    if (key === "appellation") {state.profile.appellation = value; state.collectionStep = 1;}
    if (key === "consent") {state.profile.consent = value === "yes"; state.collectionStep = value === "yes" ? 3 : 4;}
    if (key === "condition") {state.profile.condition = value === "暂不回答" ? "待补录" : value; state.collectionStep = 4;}
    log(`新用户回答${key}：${key === "consent" ? value === "yes" ? "授权" : "拒绝" : "已记录"}（模拟）`);
    render();
  } else if (target.dataset.report) {
    const data = {"gas-done":["本次产气已完成。","应答"],"neb-done":["本次雾化已完成。","应答"],"gas-remain":["产气还有十分钟，请您留意。","状态"],success:["好的，设备已执行。","应答"],failed:["设备未能执行，请稍后再试。","应答"]}[target.dataset.report];
    if (target.dataset.report === "gas-done") {state.gas = false;state.nebulizing = false;state.paused = false;}
    if (target.dataset.report === "neb-done") state.nebulizing = false;
    passive(...data);
  }
});

$("console-toggle").addEventListener("click", openConsole);
$("console-close").addEventListener("click", closeConsole);
$("scrim").addEventListener("click", closeConsole);
document.addEventListener("keydown", event => {if (event.key === "Escape") {closeConsole(); if (!$("modal-backdrop").hidden) handleAction("cancel-modal");}});

let mouthIndex = 0;
setInterval(() => {const talking = state.mode === "broadcast" || state.mode === "collect"; const mouths = ["mouth_closed", "mouth_small", "mouth_medium", "mouth_open", "mouth_medium"]; $("avatar-mouth").src = `assets/${talking ? mouths[mouthIndex++ % mouths.length] : "mouth_closed"}.png`;}, 180);

log("Web 原型已就绪；本地模拟状态，不连接真实串口或摄像头");
render();
