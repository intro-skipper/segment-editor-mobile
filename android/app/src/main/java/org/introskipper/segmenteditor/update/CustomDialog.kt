// https://github.com/BoltUIX/compose-ice-cream-template/blob/main/app/src/main/java/com/blogspot/boltuix/CustomDialog.kt

package org.introskipper.segmenteditor.update

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.introskipper.segmenteditor.MainActivity
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.theme.Purple80
import org.introskipper.segmenteditor.ui.theme.PurpleGrey40


@Composable
fun CustomDialog(openDialogCustom: MutableState<Boolean>) {
    Dialog(onDismissRequest = { openDialogCustom.value = false }) {
        CustomDialogUI(openDialogCustom = openDialogCustom)
    }
}

//Layout
@Composable
fun CustomDialogUI(modifier: Modifier = Modifier, openDialogCustom: MutableState<Boolean>) {
    val activity = LocalActivity.current as MainActivity
    Card(
        //shape = MaterialTheme.shapes.medium,
        shape = RoundedCornerShape(10.dp),
        // modifier = modifier.size(280.dp, 240.dp)
        modifier = Modifier
            .padding(10.dp, 5.dp, 10.dp, 10.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 30.dp,
                    topEnd = 0.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 30.dp
                )
            ),
    ) {
        Column(
            modifier
                .background(Color.White)) {

            //.......................................................................
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null, // decorative
                contentScale = ContentScale.Fit,
                colorFilter  = ColorFilter.tint(
                    color = colorResource( R.color.icon_orange)
                ),
                modifier = Modifier
                    .padding(top = 35.dp)
                    .height(70.dp)
                    .fillMaxWidth(),
                )

            Column(modifier = Modifier.padding(16.dp)) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.update_heading),
                    textAlign = TextAlign.Center,
                    color = colorResource( R.color.icon_orange),
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.compose.material3.Text(
                    text = stringResource(R.string.update_message),
                    textAlign = TextAlign.Center,
                    color = colorResource( R.color.icon_orange),
                    modifier = Modifier
                        .padding(top = 10.dp, start = 25.dp, end = 25.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            //.......................................................................
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                horizontalArrangement = Arrangement.SpaceAround) {

                androidx.compose.material3.TextButton(onClick = {
                    openDialogCustom.value = false
                }) {

                    androidx.compose.material3.Text(
                        stringResource(R.string.button_dismiss),
                        fontWeight = FontWeight.Bold,
                        color = PurpleGrey40,
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                    )
                }
                androidx.compose.material3.TextButton(onClick = {
                    openDialogCustom.value = false
                    activity.updateManager?.onUpdateRequested()
                }) {
                    androidx.compose.material3.Text(
                        stringResource(R.string.button_install),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview (name="Custom Dialog")
@Composable
fun DialogUIPreview(){
    CustomDialogUI(openDialogCustom = mutableStateOf(false))
}
