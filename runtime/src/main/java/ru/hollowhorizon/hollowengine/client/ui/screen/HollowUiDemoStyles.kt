package ru.hollowhorizon.hollowengine.client.ui.screen

import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss

internal val DemoStyles = compileHss(
    """
    #demo-root {
        layout: column;
        size: 100% 100%;
        min-size: 0px 0px;
        padding: 14px;
        gap: 10px;
        background: rgba(8, 10, 14, 0.92);
    }

    .tabs {
        layout: row;
        gap: 8px;
        height: 34px;
        min-size: 0px 34px;
    }

    .tab {
        layout: row;
        padding: 6px 10px;
        gap: 6px;
        width: 104px;
        background: rgba(28, 32, 42, 0.92);
        border-radius: 8px;
        border: 1px rgba(120, 140, 170, 0.38);
        hoverable: true;
        clickable: true;
        transition:
            scale 140ms ease-out,
            background 140ms ease-out;
    }

    .tab:hover {
        background: rgba(42, 48, 62, 0.96);
        scale: 1.02;
    }

    .tab:selected {
        background: rgba(54, 72, 108, 0.98);
        border: 1px rgba(145, 178, 230, 0.9);
    }

    .tab-icon {
        size: 18px 18px;
        fit: contain;
    }

    .tab-label {
        foreground: white;
    }

    .content {
        grow: 1;
        size: 100% 100%;
        min-size: 0px 0px;
        background: rgba(18, 20, 27, 0.88);
        border-radius: 12px;
        border: 1px rgba(110, 125, 150, 0.32);
        padding: 12px;
        clip: true;
    }

    .panel {
        layout: column;
        gap: 10px;
        size: 100% 100%;
        min-size: 0px 0px;
    }

    .scroll-panel {
        scrollable: true;
        size: 100% 100%;
        min-size: 0px 0px;
    }

    .title {
        foreground: #c8ddff;
        height: 18px;
    }

    .body {
        foreground: rgba(226, 230, 238, 0.92);
    }

    .row {
        layout: row;
        gap: 8px;
        padding: 8px;
        height: 48px;
        background: rgba(32, 36, 46, 0.78);
        border-radius: 8px;
    }

    .small-icon {
        size: 20px 20px;
    }

    .panel-grid {
        layout: free;
        size: 100% 100%;
        min-size: 0px 0px;
        gap: 10px;
        scrollable: true;
    }

    .card {
        layout: column;
        gap: 8px;
        padding: 10px;
        size: 168px 118px;
        background: rgba(30, 34, 44, 0.9);
        border-radius: 10px;
        border: 1px rgba(120, 140, 170, 0.35);
        transition:
            scale 140ms ease-out,
            rotate 140ms ease-out,
            background 140ms ease-out;
    }

    .card:hover {
        background: rgba(42, 48, 62, 0.96);
        scale: 1.1;
    }

    .card-title {
        foreground: #c8ddff;
        height: 16px;
    }

    .tilted-x {
        transition:
            rotate 180ms ease-out,
            scale 90ms ease-out,
            background 120ms ease-out;
    }

    .preview-image {
        size: 72px 72px;
        image-fit: contain;
    }

    .item-preview {
        size: 52px 52px;
    }

    .entity-preview {
        size: 72px 72px;
    }

    .canvas-preview {
        size: 120px 54px;
    }

    .free-stage {
        layout: free;
        size: 100% 100%;
        min-size: 0px 0px;
        background: linear-gradient(135deg, rgba(12, 16, 23, 0.94), rgba(18, 44, 54, 0.9), rgba(38, 42, 64, 0.88));
        border-radius: 10px;
        scrollable: true;
    }

    .free-node {
        padding: 8px;
        size: 92px 42px;
        background: rgba(48, 62, 88, 0.95);
        border-radius: 8px;
        border: 1px rgba(136, 174, 230, 0.65);
        transition:
            scale 180ms ease-out,
            background 180ms ease-out;
    }

    .free-node:hover {
        scale: 1.03;
        background: rgba(62, 78, 110, 0.98);
    }

    .free-node:dragging {
        scale: 1.08;
        background: rgba(78, 96, 134, 1.0);
    }

    .free-label {
        foreground: white;
        height: 16px;
    }

    .layout-glass {
        layout: column;
        gap: 8px;
        padding: 12px;
        size: 184px 92px;
        border-radius: 12px;
        border: 1px rgba(198, 222, 250, 0.34);
        background: rgba(28, 36, 50, 0.28);
        foreground: rgba(226, 238, 255, 0.95);
        backdrop-filter: blur(9px) grayscale(0.08);
        shadow: none;
        draggable: true;
        hoverable: true;
        transition:
            translate 120ms ease-out,
            shadow 160ms ease-out,
            background 140ms ease-out;
    }

    .layout-glass:hover {
        background: rgba(42, 56, 76, 0.34);
        shadow: none;
    }

    .effects-stage {
        layout: free;
        size: 100% 100%;
        min-size: 0px 0px;
        scrollable: true;
        background: linear-gradient(135deg, rgba(12, 18, 28, 0.98), rgba(28, 34, 48, 0.94), rgba(18, 42, 48, 0.92));
        border-radius: 10px;
    }

    .effect-card {
        layout: column;
        gap: 8px;
        padding: 12px;
        size: 178px 126px;
        border-radius: 14px;
        border: 2px rgba(232, 238, 255, 0.34);
        background: rgba(32, 39, 54, 0.9);
        foreground: rgba(238, 242, 250, 0.96);
        shadow: 0px 18px 14px -4px rgba(0, 0, 0, 0.66);
        transition:
            filter 180ms ease-out,
            rotate 240ms ease-out,
            translate 180ms ease-out,
            shadow 180ms ease-out,
            scale 140ms ease-out,
            background 160ms ease-out;
    }

    .gradient-card {
        background: linear-gradient(135deg, rgba(70, 132, 218, 0.96), rgba(142, 83, 184, 0.94), rgba(34, 172, 148, 0.9));
        shadow: 0px 22px 40px -6px rgba(8, 18, 34, 0.72);
    }

    .grayscale-card {
        filter: grayscale(1);
        background: linear-gradient(145deg, rgba(86, 112, 152, 0.94), rgba(42, 52, 72, 0.96));
        border: 2px rgba(218, 230, 246, 0.5);
        hoverable: true;
    }

    .grayscale-card:hover {
        filter: grayscale(0);
        scale: 1.04;
        border: 2px rgba(218, 230, 246, 0.5);
    }

    .flip-zone {
        layout: free;
        size: 178px 126px;
        hoverable: true;
        perspective: 680px;
    }

    .flip-face {
        backface-visibility: hidden;
        perspective: 680px;
        shadow: none;
        transition:
            rotate 460ms ease-out,
            shadow 380ms ease-out;
    }

    .flip-front {
        background: linear-gradient(135deg, rgba(58, 102, 156, 0.97), rgba(36, 48, 74, 0.97));
    }

    .flip-back {
        background: linear-gradient(135deg, rgba(38, 154, 130, 0.96), rgba(38, 62, 78, 0.98));
        border: 2px rgba(174, 255, 228, 0.44);
    }

    .paper-card {
        hoverable: true;
        perspective: 720px;
        background: linear-gradient(180deg, rgba(242, 235, 212, 0.96), rgba(206, 218, 230, 0.94));
        foreground: rgba(22, 28, 36, 0.98);
        border: 2px rgba(255, 255, 255, 0.62);
    }

    .paper-card:hover {
        rotate: -12deg 10deg 0deg;
        translate: 0px -8px 18px;
    }

    .paper-title {
        foreground: rgba(28, 34, 48, 0.98);
    }

    .paper-body {
        foreground: rgba(48, 54, 68, 0.94);
    }

    .glass-card {
        background: rgba(34, 42, 58, 0.42);
        border: 2px rgba(232, 246, 255, 0.38);
        backdrop-filter: blur(16px) grayscale(0.15);
    }

    .css-lift-card {
        hoverable: true;
        perspective: 800px;
        rotate: 51deg 0deg 43deg;
        background: rgba(248, 250, 253, 0.96);
        foreground: rgba(30, 34, 52, 0.96);
        border: 1px rgba(255, 255, 255, 0.72);
        transition:
            translate 240ms ease-out,
            rotate 240ms ease-out;
    }

    .css-lift-card:hover {
        translate: 0px -16px 0px;
    }

    .soft-focus-card {
        hoverable: true;
        perspective: 800px;
        rotate: 10deg -25deg 0deg;
        scale: 0.9;
        opacity: 0.55;
        filter: blur(8px);
        background: linear-gradient(135deg, rgba(236, 126, 168, 0.92), rgba(126, 154, 246, 0.92));
        foreground: rgba(246, 248, 255, 0.96);
        shadow: 24px 24px 34px -8px rgba(12, 12, 28, 0.68);
        transition:
            filter 260ms ease-out,
            opacity 260ms ease-out,
            scale 260ms ease-out,
            rotate 260ms ease-out;
    }

    .soft-focus-card:hover {
        rotate: 10deg 15deg 0deg;
        scale: 1;
        opacity: 1;
        filter: blur(0px);
    }

    .soft-title {
        foreground: rgba(250, 252, 255, 0.98);
    }

    .soft-body {
        foreground: rgba(250, 252, 255, 0.94);
    }
    """.trimIndent()
)
