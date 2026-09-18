package com.finunity.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors

/**
 * In-app privacy notice. The public Play Console privacy-policy URL must contain the same
 * information and a real developer support contact before production submission.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { FinTopBar("隐私与数据说明", onBack) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("衡仓隐私政策", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("生效日期：2026-08-22", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)

            PolicySection("1. 我们处理哪些数据") {
                Text("衡仓用于记录个人资产、账户、持仓、交易、目标配置和复盘数据。这些数据默认保存在本机 Room 数据库中。应用不要求注册登录，不建立衡仓云端用户账户，也不提供券商交易执行。")
            }
            PolicySection("2. 网络请求") {
                Text("当你主动刷新行情或后台同步运行时，应用只会向 FinUnity 专属服务请求证券价格、基金净值和汇率。请求包含证券代码、资产类型和货币对，不包含持仓数量、成本、账户余额或交易明细。专属服务负责连接实际数据源，并返回数据时间、来源和质量状态。")
            }
            PolicySection("3. 截图识别") {
                Text("只有在你明确确认后，所选持仓截图才会通过加密连接发送到 FinUnity 专属服务进行结构化解析。识别结果需逐项确认后才会保存到本机；服务报错会直接显示，不会用模拟数据补齐。原图的处理和删除期限以公开隐私政策为准。")
            }
            PolicySection("4. 备份、保存与删除") {
                Text("你可以在“备份恢复”中主动导出或恢复 JSON 文件。导出的 JSON 可能包含完整财务信息，属于明文文件，请只保存到可信位置。应用已关闭 Android 自动云备份；删除应用或清空应用数据会移除本机数据库。")
            }
            PolicySection("5. 数据共享") {
                Text("除你主动选择的行情请求和系统文件选择器外，应用不向广告、分析或社交服务共享个人投资数据。应用不包含广告 SDK、登录 SDK 或第三方统计 SDK。")
            }
            PolicySection("6. 联系方式") {
                Text("请在发布前将本节替换为开发者的真实支持邮箱，并将本政策发布到公开、可访问且非 PDF 的网页地址，再填写到 Google Play Console 和商店详情中。")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicySection(title: String, content: @Composable () -> Unit) {
    FinCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
