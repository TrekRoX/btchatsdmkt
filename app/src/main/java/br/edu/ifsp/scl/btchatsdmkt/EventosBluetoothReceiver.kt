package br.edu.ifsp.scl.btchatsdmkt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/* BroadcastReceiver que vai tratar os eventos de dispositivo encontrado (ACTION_FOUND) e fim das buscas (ACTION_DISCOVERY_FINISHED) no modo cliente */
class EventosBluetoothReceiver(val mainActivity: MainActivity): BroadcastReceiver(){
    /* O método onReceive é chamado pelo SO se um evento assim acontecer e se a Activity tiver manifestado interesse por tratar esses eventos, como foi feito no onCreate com a chamada de registerReceiver */
    override fun onReceive(context: Context, intent: Intent){
        if (BluetoothDevice.ACTION_FOUND == intent.action){
            // Recupera o dispositivo encontrado
            val dispositivoEncontrado: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

            // Adiciona o dispositivo encontrado na Lista de dispositivos encontrados
            mainActivity.listaBtsEncontrados?.add(dispositivoEncontrado)
        } else {
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                // Quando a busca termina (timeout) os dispositivos são mostrados para o usuário.
                mainActivity.exibirDispositivosEncontrados()

                // Desregistra para descoberta após a busca
                mainActivity.desregistraReceiver()
            }
        }
    }
}