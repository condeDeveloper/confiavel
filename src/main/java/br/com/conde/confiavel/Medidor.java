package br.com.conde.confiavel;

import java.util.Locale;

/**
 * O MEDIDOR: imprime as tabelas que o README mostra e que os testes conferem.
 *
 * <p>Toda tabela daqui tem um teste do lado que afirma os mesmos numeros. Texto nao quebra
 * quando deixa de ser verdade; teste quebra.
 */
public final class Medidor {

    private Medidor() {
    }

    public static void main(String[] args) {
        String comando = args.length > 0 ? args[0] : "tudo";

        if (comando.equals("correcao") || comando.equals("tudo")) correcao();
        if (comando.equals("numeracao") || comando.equals("tudo")) numeracao();
        if (comando.equals("custo") || comando.equals("tudo")) custo();
        if (comando.equals("enchendo") || comando.equals("tudo")) enchendo();
        if (comando.equals("desordem") || comando.equals("tudo")) desordem();
    }

    private static void correcao() {
        System.out.println();
        System.out.println("O FLUXO QUE CHEGA E EXATAMENTE O QUE SAIU?");
        System.out.println("Comparacao byte a byte com o vetor original, em "
                + Medidas.EXECUCOES + " execucoes por linha,");
        System.out.println("com perda aplicada a cada quadro nos DOIS sentidos.");
        System.out.println();

        System.out.printf("  %-20s %8s %12s %14s %14s %12s %14s%n",
                "protocolo", "perda", "execucoes", "fluxo errado", "nao terminou",
                "fatias", "transmissoes");

        for (Medidas.Correcao linha : Medidas.correcao())
            System.out.printf(Locale.ROOT, "  %-20s %7.0f%% %12d %14d %14d %12d %14d%n",
                    linha.protocolo(), 100 * linha.perda(), linha.execucoes(),
                    linha.erraramOFluxo(), linha.naoTerminaram(), linha.fatias(),
                    linha.transmissoes());

        System.out.println();
        System.out.println("  " + Medidas.TAMANHO + " bytes, espaco de numeracao 8, cada"
                + " protocolo na maior janela que ele aguenta.");
        System.out.println();
    }

    private static void numeracao() {
        System.out.println();
        System.out.println("A JANELA QUE PASSA DA METADE DO ESPACO DE NUMEROS");
        System.out.println("A janela sobe de um em um e o juiz diz quando o fluxo entregue"
                + " para de bater.");
        System.out.println("Perda de 20%, canal que entrega em ordem, "
                + Medidas.EXECUCOES_DA_NUMERACAO + " execucoes por casa.");
        System.out.println();

        for (int espaco : Medidas.ESPACOS) {
            System.out.printf("  espaco de numeracao %d%n", espaco);
            System.out.printf("  %-20s", "protocolo");

            for (int janela = 1; janela <= espaco; janela++)
                System.out.printf(" %7s", "j" + janela);

            System.out.println();

            for (String nome : Medidas.nomes()) {
                System.out.printf("  %-20s", nome);

                for (int janela = 1; janela <= espaco; janela++) {
                    int erros = achar(nome, espaco, janela);

                    System.out.printf(" %7s", erros == 0 ? "ok" : erros + " err");
                }

                System.out.println();
            }

            System.out.println();
        }
    }

    private static int achar(String nome, int espaco, int janela) {
        return Medidas.numeracao().stream()
                .filter(linha -> linha.protocolo().equals(nome))
                .filter(linha -> linha.espaco() == espaco)
                .filter(linha -> linha.janela() == janela)
                .findFirst().orElseThrow().erraramOFluxo();
    }

    private static void custo() {
        System.out.println();
        System.out.println("QUANTO CADA PROTOCOLO GASTA COM PERDA");
        System.out.println("Espaco de numeracao 32, janela 8 para quem aguenta, atraso de "
                + Medidas.ATRASO + " fatias.");
        System.out.println();

        System.out.printf("  %-20s %8s %22s %26s%n",
                "protocolo", "perda", "transmissoes por byte", "retransmitidos por perdido");

        for (Medidas.Custo linha : Medidas.custos())
            System.out.printf(Locale.ROOT, "  %-20s %7.0f%% %22.3f %26.2f%n",
                    linha.protocolo(), 100 * linha.perda(), linha.transmissoesPorByte(),
                    linha.retransmissoesPorPerda());

        System.out.println();
    }

    private static void enchendo() {
        System.out.println();
        System.out.println("A JANELA QUE O ENLACE COMPORTA");
        System.out.println("Sem perda. A ida e volta e de " + (2 * Medidas.ATRASO)
                + " fatias, entao a formula e a janela sobre " + (2 * Medidas.ATRASO) + ",");
        System.out.println("limitada pela primeira ida e volta, que nenhuma janela recupera.");
        System.out.println();

        System.out.printf("  %-20s %10s %12s %12s %10s%n",
                "protocolo", "janela", "uso medido", "formula", "razao");

        for (Medidas.Enchimento linha : Medidas.enchimento())
            System.out.printf(Locale.ROOT, "  %-20s %10d %12.4f %12.4f %10.4f%n",
                    linha.protocolo(), linha.janela(), linha.uso(), linha.formula(),
                    linha.uso() / linha.formula());

        System.out.println();
    }

    private static void desordem() {
        System.out.println();
        System.out.println("O QUE A ENTREGA FORA DE ORDEM FAZ COM A REGRA");
        System.out.println("A maior janela que entrega o fluxo certo em todas as execucoes,"
                + " com espaco 8.");
        System.out.println();

        System.out.printf("  %-20s %16s %16s %18s%n",
                "protocolo", "regra do livro", "medido em ordem", "medido desordenando");

        for (Medidas.Desordem linha : Medidas.desordem())
            System.out.printf("  %-20s %16d %16d %18d%n",
                    linha.protocolo(), linha.regraDoLivro(), linha.maiorEmOrdem(),
                    linha.maiorDesordenando());

        System.out.println();
        System.out.println("  O canal desordena em ate " + (Canal.DESORDENA - 1)
                + " fatias, que e menos que a ida e volta inteira.");
        System.out.println("  A parada e espera manda um quadro de cada vez e nao olha a"
                + " janela: a linha dela mede o espaco, nao a janela.");
        System.out.println();
    }
}
