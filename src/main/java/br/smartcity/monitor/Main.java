package br.smartcity.monitor;

import br.smartcity.monitor.ui.SmartCityApplication;

/** Ponto de entrada: abre o dashboard; use --cli para a execução textual. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0 && "--cli".equals(args[0])) {
            String[] argumentosCli = java.util.Arrays.copyOfRange(args, 1, args.length);
            ConsoleMain.executar(argumentosCli);
            return;
        }
        SmartCityApplication.main(args);
    }
}
