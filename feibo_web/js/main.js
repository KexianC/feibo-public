const WORKBENCH_URL = "https://dd.hzmwmt.com";

const productPanels = {
  zhibi: {
    label: "MEDSCRIBE",
    title: "智笔医言",
    desc: "面向医生、专家与医学内容团队的 AI 对话入口，可按专科智能体进行问答、生成科普内容、沉淀历史会话。",
    image: "./assets/product-zhibi.png",
    alt: "智笔医言医学内容智能工作台界面",
    features: ["医学内容智能工作台", "模型与智能体选择", "对话历史与内容导出"],
  },
  zhihe: {
    label: "MEDPROOF",
    title: "智核医审",
    desc: "围绕医学文案建立审核列表、状态筛选与审批记录，帮助医生、专家、平台角色完成内容质量把关。",
    image: "./assets/product-zhihe.png",
    alt: "智核医审医学内容审核工作台界面",
    features: ["医生自审与专家审批", "平台终审与驳回记录", "文案分类与来源管理"],
  },
  zhihui: {
    label: "MEDVID",
    title: "智绘医合",
    desc: "将审核后的医学文案连接数字人、声音、模板与视频合成能力，用于健康科普、宣教说明和医学知识视频生产。",
    image: "./assets/product-zhihui.png",
    alt: "智绘医合数字医生视频合成视觉",
    features: ["数字人角色选择", "脚本回填与语音驱动", "医疗科普视频合成"],
  },
};

const workflowText = [
  "选择医学智能体，输入病例科普、健康宣教或内容创作需求，由智笔医言生成结构化初稿。",
  "把生成内容提交到智核医审，经过医生、专家和平台环节审阅，保留审批状态和记录。",
  "审核后的脚本进入智绘医合，结合数字人、声音和场景模板生成医学科普视频。",
  "视频、文案和会话沉淀在工作台资源中，便于团队复用、追踪和继续生产。",
];

const caseVideos = [
  {
    title: "健康知识普及宣传视频",
    desc: "向观众普及健康知识和小贴士，帮助大家更好地了解如何保持身体健康。",
    tags: ["数字人", "健康科普", "疾病预防"],
    cover: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710398070868.png",
    video: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473495131.mp4",
    hot: true,
  },
  {
    title: "血管介入诊疗知情同意书",
    desc: "围绕介入诊疗前的治疗过程、风险和可能并发症进行视频化说明。",
    tags: ["介入治疗", "知情同意", "医疗科普"],
    cover: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710398132347.png",
    video: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473502027.mp4",
    hot: false,
  },
  {
    title: "喉炎会转化为喉癌吗",
    desc: "从医学角度分析喉炎和喉癌风险，辅助观众理解疾病早筛和诊断知识。",
    tags: ["癌症", "早期诊断", "医学解释"],
    cover: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397910840.png",
    video: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473485563.mp4",
    hot: true,
  },
  {
    title: "皮肤擦伤怎么办",
    desc: "讲解清洁伤口、消毒处理和敷料选择等步骤，减少感染并促进伤口愈合。",
    tags: ["伤口处理", "日常急救", "健康科普"],
    cover: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397811219.png",
    video: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473473392.mp4",
    hot: false,
  },
  {
    title: "什么是身高管理",
    desc: "介绍营养、生活方式和运动干预等健康措施，帮助青少年家庭理解身高管理。",
    tags: ["青少年", "身高管理", "生活方式"],
    cover: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/ent/2024314/mbcase_1710397501278.png",
    video: "https://feibo-oss.oss-cn-hangzhou.aliyuncs.com/manage/2024315/mbcase_1710473609418.mp4",
    hot: true,
  },
];

document.querySelectorAll('a[href="https://dd.hzmwmt.com"]').forEach((link) => {
  link.href = WORKBENCH_URL;
});

const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

function setTheme(mode) {
  document.body.classList.toggle("dark-mode", mode === "dark");
  localStorage.setItem("feibo-site-theme", mode);
  const toggle = document.querySelector("[data-theme-toggle]");
  const label = mode === "dark" ? "切换浅色模式" : "切换深色模式";
  toggle?.setAttribute("aria-label", label);
  toggle?.setAttribute("title", label);
}

function initTheme() {
  const saved = localStorage.getItem("feibo-site-theme");
  const initial = saved || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  setTheme(initial);
  document.querySelector("[data-theme-toggle]")?.addEventListener("click", () => {
    setTheme(document.body.classList.contains("dark-mode") ? "light" : "dark");
  });
}

function initNavigation() {
  const nav = document.querySelector("[data-nav]");
  const navToggle = document.querySelector("[data-nav-toggle]");
  const navLinks = [...document.querySelectorAll(".site-nav a")];

  navToggle?.addEventListener("click", () => {
    const isOpen = nav?.classList.toggle("is-open");
    navToggle.setAttribute("aria-label", isOpen ? "关闭导航" : "打开导航");
  });

  navLinks.forEach((link) => {
    link.addEventListener("click", () => nav?.classList.remove("is-open"));
  });

  const sections = navLinks
    .map((link) => document.querySelector(link.getAttribute("href")))
    .filter(Boolean);

  if (!sections.length) return;

  const observer = new IntersectionObserver(
    (entries) => {
      const active = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];

      if (!active) return;

      navLinks.forEach((link) => {
        link.classList.toggle("is-active", link.getAttribute("href") === `#${active.target.id}`);
      });
    },
    { rootMargin: "-38% 0px -48% 0px", threshold: [0.1, 0.4, 0.7] },
  );

  sections.forEach((section) => observer.observe(section));
}

function initProductTabs() {
  const stage = document.querySelector(".product-stage");
  const image = document.querySelector("[data-product-image]");
  const label = document.querySelector("[data-product-label]");
  const title = document.querySelector("[data-product-title]");
  const desc = document.querySelector("[data-product-desc]");
  const featureList = document.querySelector("[data-product-features]");
  const tabs = [...document.querySelectorAll("[data-product]")];

  function renderProduct(key) {
    const panel = productPanels[key];
    if (!panel || !image || !label || !title || !desc || !featureList) return;

    tabs.forEach((tab) => {
      const active = tab.dataset.product === key;
      tab.classList.toggle("is-active", active);
      tab.setAttribute("aria-selected", String(active));
    });

    stage?.classList.add("is-switching");

    window.setTimeout(() => {
      image.src = panel.image;
      image.alt = panel.alt;
      label.textContent = panel.label;
      title.textContent = panel.title;
      desc.textContent = panel.desc;
      featureList.replaceChildren(
        ...panel.features.map((feature) => {
          const item = document.createElement("li");
          item.textContent = feature;
          return item;
        }),
      );
      stage?.classList.remove("is-switching");
    }, 140);
  }

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => renderProduct(tab.dataset.product));
  });
}

function initWorkflow() {
  const steps = [...document.querySelectorAll("[data-step]")];
  const text = document.querySelector("[data-workflow-text]");
  const meter = document.querySelector("[data-workflow-meter]");

  function setStep(index) {
    steps.forEach((step, stepIndex) => {
      step.classList.toggle("is-active", stepIndex === index);
    });

    if (text) text.textContent = workflowText[index];
    if (meter) meter.style.width = `${(index + 1) * 25}%`;
  }

  steps.forEach((step) => {
    step.addEventListener("click", () => setStep(Number(step.dataset.step)));
  });
}

function createTextElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  element.textContent = text;
  return element;
}

function formatVideoDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds <= 0) return "--:--";

  const totalSeconds = Math.round(seconds);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const remainingSeconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, "0")}:${String(remainingSeconds).padStart(2, "0")}`;
  }

  return `${minutes}:${String(remainingSeconds).padStart(2, "0")}`;
}

function getVideoDuration(videoUrl) {
  return new Promise((resolve, reject) => {
    const video = document.createElement("video");
    const timeout = window.setTimeout(() => {
      video.removeAttribute("src");
      video.load();
      reject(new Error("Video metadata timed out"));
    }, 12000);

    video.preload = "metadata";

    video.addEventListener(
      "loadedmetadata",
      () => {
        window.clearTimeout(timeout);
        const duration = video.duration;
        video.removeAttribute("src");
        video.load();
        resolve(formatVideoDuration(duration));
      },
      { once: true },
    );

    video.addEventListener(
      "error",
      () => {
        window.clearTimeout(timeout);
        reject(new Error("Video metadata failed"));
      },
      { once: true },
    );

    video.src = videoUrl;
  });
}

function renderCases() {
  const grid = document.querySelector("[data-case-grid]");
  if (!grid) return;

  const cards = caseVideos.map((item, index) => {
    const article = document.createElement("article");
    article.className = "case-card reveal";

    const media = document.createElement("div");
    media.className = "case-card__media";

    const img = document.createElement("img");
    img.src = item.cover;
    img.alt = item.title;
    img.loading = "lazy";
    media.appendChild(img);

    if (item.hot) {
      media.appendChild(createTextElement("span", "case-card__badge", "热门"));
    }

    const duration = createTextElement("span", "case-card__duration", "读取中");
    duration.dataset.durationIndex = String(index);
    media.appendChild(duration);

    const body = document.createElement("div");
    body.className = "case-card__body";
    body.appendChild(createTextElement("h3", "", item.title));
    body.appendChild(createTextElement("p", "", item.desc));

    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "预览案例";
    button.addEventListener("click", () => openVideoDialog(item));
    body.appendChild(button);

    article.append(media, body);
    return article;
  });

  grid.replaceChildren(...cards);
  updateCaseDurations();
}

function updateCaseDurations() {
  caseVideos.forEach((item, index) => {
    const duration = document.querySelector(`[data-duration-index="${index}"]`);
    if (!duration || !item.video) return;

    getVideoDuration(item.video)
      .then((actualDuration) => {
        item.duration = actualDuration;
        duration.textContent = actualDuration;
      })
      .catch(() => {
        duration.textContent = "--:--";
        duration.title = "未能读取视频真实时长";
      });
  });
}

function openVideoDialog(item) {
  const dialog = document.querySelector("[data-video-dialog]");
  const title = document.querySelector("[data-dialog-title]");
  const desc = document.querySelector("[data-dialog-desc]");
  const video = document.querySelector("[data-dialog-video]");

  if (!dialog || !title || !desc || !video) return;

  title.textContent = item.title;
  desc.textContent = item.desc;
  video.poster = item.cover;
  video.src = item.video;

  if (typeof dialog.showModal === "function") {
    dialog.showModal();
  } else {
    dialog.setAttribute("open", "");
  }
}

function initVideoDialog() {
  const dialog = document.querySelector("[data-video-dialog]");
  const close = document.querySelector("[data-dialog-close]");
  const video = document.querySelector("[data-dialog-video]");

  function closeDialog() {
    video?.pause();
    if (video) video.removeAttribute("src");

    if (dialog?.open && typeof dialog.close === "function") {
      dialog.close();
    } else {
      dialog?.removeAttribute("open");
    }
  }

  close?.addEventListener("click", closeDialog);
  dialog?.addEventListener("cancel", (event) => {
    event.preventDefault();
    closeDialog();
  });
  dialog?.addEventListener("click", (event) => {
    if (event.target === dialog) closeDialog();
  });
}

function initReveal() {
  const items = [...document.querySelectorAll(".reveal")];

  if (!items.length) return;

  if (!("IntersectionObserver" in window) || prefersReducedMotion) {
    items.forEach((item) => item.classList.add("is-visible"));
    return;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.18 },
  );

  items.forEach((item) => observer.observe(item));
}

function initParticles() {
  const canvas = document.querySelector("#particle-canvas");
  if (!canvas || prefersReducedMotion) return;

  const ctx = canvas.getContext("2d");
  const pointer = { x: null, y: null };
  let particles = [];
  let width = 0;
  let height = 0;
  let deviceScale = 1;

  function resetCanvas() {
    deviceScale = Math.min(window.devicePixelRatio || 1, 2);
    width = window.innerWidth;
    height = window.innerHeight;
    canvas.width = Math.floor(width * deviceScale);
    canvas.height = Math.floor(height * deviceScale);
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    ctx.setTransform(deviceScale, 0, 0, deviceScale, 0, 0);

    const count = Math.min(120, Math.max(48, Math.floor((width * height) / 15000)));
    particles = Array.from({ length: count }, () => ({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.38,
      vy: (Math.random() - 0.5) * 0.38,
      r: Math.random() * 1.8 + 0.8,
    }));
  }

  function draw() {
    ctx.clearRect(0, 0, width, height);
    const dark = document.body.classList.contains("dark-mode");
    const pointColor = dark ? "rgba(72, 199, 183, 0.7)" : "rgba(15, 157, 145, 0.52)";
    const lineColor = dark ? "rgba(105, 169, 223, 0.22)" : "rgba(47, 121, 185, 0.18)";
    const activeColor = dark ? "rgba(228, 180, 102, 0.42)" : "rgba(198, 134, 47, 0.38)";

    particles.forEach((point, index) => {
      point.x += point.vx;
      point.y += point.vy;

      if (point.x < -20) point.x = width + 20;
      if (point.x > width + 20) point.x = -20;
      if (point.y < -20) point.y = height + 20;
      if (point.y > height + 20) point.y = -20;

      if (pointer.x !== null && pointer.y !== null) {
        const dx = pointer.x - point.x;
        const dy = pointer.y - point.y;
        const dist = Math.hypot(dx, dy);

        if (dist < 150 && dist > 0.1) {
          point.x -= (dx / dist) * 0.32;
          point.y -= (dy / dist) * 0.32;
        }
      }

      for (let i = index + 1; i < particles.length; i += 1) {
        const other = particles[i];
        const dx = other.x - point.x;
        const dy = other.y - point.y;
        const dist = Math.hypot(dx, dy);

        if (dist < 128) {
          ctx.strokeStyle = lineColor;
          ctx.globalAlpha = 1 - dist / 128;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(point.x, point.y);
          ctx.lineTo(other.x, other.y);
          ctx.stroke();
          ctx.globalAlpha = 1;
        }
      }

      if (pointer.x !== null && pointer.y !== null) {
        const pointerDist = Math.hypot(pointer.x - point.x, pointer.y - point.y);

        if (pointerDist < 180) {
          ctx.strokeStyle = activeColor;
          ctx.globalAlpha = 1 - pointerDist / 180;
          ctx.beginPath();
          ctx.moveTo(point.x, point.y);
          ctx.lineTo(pointer.x, pointer.y);
          ctx.stroke();
          ctx.globalAlpha = 1;
        }
      }

      ctx.fillStyle = pointColor;
      ctx.beginPath();
      ctx.arc(point.x, point.y, point.r, 0, Math.PI * 2);
      ctx.fill();
    });

    requestAnimationFrame(draw);
  }

  window.addEventListener("resize", resetCanvas, { passive: true });
  window.addEventListener(
    "pointermove",
    (event) => {
      pointer.x = event.clientX;
      pointer.y = event.clientY;
    },
    { passive: true },
  );
  window.addEventListener("pointerleave", () => {
    pointer.x = null;
    pointer.y = null;
  });

  resetCanvas();
  draw();
}

function initHeroParallax() {
  const doctors = document.querySelector(".hero__doctors");
  const hud = document.querySelector(".hero__hud");
  if (!doctors || prefersReducedMotion) return;

  window.addEventListener(
    "pointermove",
    (event) => {
      const x = (event.clientX / window.innerWidth - 0.5) * 12;
      const y = (event.clientY / window.innerHeight - 0.5) * 8;
      doctors.style.transform = `translate(${x}px, ${y}px)`;
      if (hud) hud.style.transform = `translate(${-x * 0.4}px, ${-y * 0.4}px)`;
    },
    { passive: true },
  );
}

function initWorkbenchLinks() {
  document.querySelectorAll('a[href^="https://dd.hzmwmt.com"]').forEach((link) => {
    link.addEventListener("click", () => {
      link.setAttribute("data-clicked", "true");
    });
  });
}

document.addEventListener("DOMContentLoaded", () => {
  initTheme();
  initNavigation();
  initProductTabs();
  initWorkflow();
  renderCases();
  initVideoDialog();
  initReveal();
  initParticles();
  initHeroParallax();
  initWorkbenchLinks();
});
