package com.kingzcheung.xime.ui.settings

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * 用本地 KaTeX（assets/katex）渲染公式。
 *
 * 公式过宽/过高时由页面脚本自动缩小字号；颜色跟随传入的键面文字色，
 * 键盘里直接复用它做公式预览。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun KatexView(latex: String, modifier: Modifier = Modifier, color: Color? = null) {
    val textColor = (color ?: MaterialTheme.colorScheme.onSurface).toArgb()
    val colorCss = String.format("#%06X", 0xFFFFFF and textColor)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { web ->
            val key = "$colorCss|$latex"
            if (web.tag != key) {
                web.tag = key
                web.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    katexHtml(latex, colorCss),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

private fun katexHtml(latex: String, color: String): String {
    val texJs = JSONObject.quote(latex)
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <link rel="stylesheet" href="katex.min.css"><script src="katex.min.js"></script>
        <style>
          html,body{margin:0;padding:0;background:transparent;color:$color;overflow:hidden}
          #f{position:fixed;left:0;top:0;right:0;bottom:0;display:flex;align-items:center;justify-content:center;white-space:nowrap}
          #m{font-size:26px}
          .katex-display{margin:0}
        </style></head><body><div id="f"><div id="m"></div></div><script>
          var m=document.getElementById('m');
          try{katex.render($texJs,m,{displayMode:true,throwOnError:false,strict:false});}
          catch(e){m.textContent=$texJs;}
          function fit(){
            var w=innerWidth-8,h=innerHeight-4;
            if(w<=0||h<=0)return;
            m.style.fontSize='26px';
            var r=Math.min(1,w/m.scrollWidth,h/m.scrollHeight);
            m.style.fontSize=Math.max(8,Math.floor(26*r))+'px';
          }
          fit();addEventListener('resize',fit);
          if(document.fonts)document.fonts.ready.then(fit);
        </script></body></html>
    """.trimIndent()
}
