package br.com.conde.confiavel;

import java.util.ArrayList;
import java.util.List;

/**
 * AS MEDIDAS: tudo o que o repositorio afirma, calculado aqui e em nenhum outro lugar.
 *
 * <p>O Medidor imprime o que sai daqui e o teste confere o que sai daqui. Os dois leem a
 * MESMA funcao, entao nao existe a situacao de o texto dizer uma coisa e o teste conferir
 * outra: se um numero mudar, a tabela muda e o teste quebra junto.
 */
public final class Medidas {

    /** Quantos bytes tem o fluxo a entregar. */
    public static final int TAMANHO = 200;

    /** Quantas sementes diferentes entram em cada ponto das tabelas. */
    public static final int EXECUCOES = 30;

    /** Quantas sementes entram na tabela da numeracao, que procura um erro raro. */
    public static final int EXECUCOES_DA_NUMERACAO = 40;

    /** O atraso de ida, em fatias. A ida e volta e o dobro disso. */
    public static final int ATRASO = 5;

    /** As taxas de perda medidas, aplicadas a cada quadro em cada sentido. */
    public static final double[] PERDAS = {0, 0.02, 0.05, 0.1, 0.2, 0.3};

    public static final long SEMENTE_DO_FLUXO = 99;

    private Medidas() {}

    private static byte[] fluxo;

    public static byte[] fluxo() {
        if (fluxo != null) return fluxo;

        return fluxo = new Sorteio(SEMENTE_DO_FLUXO).fluxo(TAMANHO);
    }

    /** Uma transmissao inteira, com canal igual nos dois sentidos. */
    public static Protocolo.Resultado rodar(Protocolo protocolo, int espaco, int janela,
            double perda, int sacudida, long semente) {

        Sorteio sorteio = new Sorteio(semente);

        Canal ida = new Canal(ATRASO, perda, sacudida, sorteio);
        Canal volta = new Canal(ATRASO, perda, sacudida, sorteio);

        return protocolo.entregar(fluxo(), ida, volta, espaco, janela,
                2 * ATRASO + sacudida + 2, 200_000);
    }

    /** Um protocolo novo com o mesmo nome, porque cada transmissao tem estado proprio. */
    public static Protocolo novo(String nome) {
        return switch (nome) {
            case "parada e espera" -> new ParadaEEspera();
            case "volta N" -> new VoltaN();
            case "repeticao seletiva" -> new RepeticaoSeletiva();
            default -> throw new IllegalArgumentException("protocolo desconhecido: " + nome);
        };
    }

    public static List<String> nomes() {
        return Protocolo.todos().stream().map(Protocolo::nome).toList();
    }

    /* ===================== 1. correcao ===================== */

    public record Correcao(String protocolo, double perda, int execucoes, int erraramOFluxo,
            int naoTerminaram, long fatias, long transmissoes) {}

    private static List<Correcao> correcao;

    /**
     * O JUIZ: o fluxo entregue e exatamente o fluxo que saiu?
     *
     * <p>Nao e "quase todos os bytes" nem "os bytes certos em alguma ordem". A comparacao e
     * byte a byte com o vetor original, em toda execucao, com perda de ate quarenta por
     * cento nos dois sentidos.
     *
     * <p>Nenhuma propriedade interna do protocolo substitui isso. Um protocolo pode estar
     * perfeitamente coerente consigo mesmo, confirmar tudo o que recebe e entregar o fluxo
     * errado, e e exatamente isso que a tabela da numeracao mostra acontecendo.
     */
    public static List<Correcao> correcao() {
        if (correcao != null) return correcao;

        List<Correcao> saida = new ArrayList<>();

        for (String nome : nomes())
            for (double perda : new double[] {0, 0.05, 0.2, 0.4}) {
                int espaco = 8;
                int janela = novo(nome).maiorJanela(espaco);

                int erraram = 0;
                int naoTerminaram = 0;

                long fatias = 0;
                long transmissoes = 0;

                for (int semente = 1; semente <= EXECUCOES; semente++) {
                    Protocolo.Resultado resultado =
                            rodar(novo(nome), espaco, janela, perda, Canal.EM_ORDEM, semente);

                    if (!resultado.terminou()) naoTerminaram++;
                    else if (!resultado.correto(fluxo())) erraram++;

                    fatias += resultado.fatias();
                    transmissoes += resultado.transmissoes();
                }

                saida.add(new Correcao(nome, perda, EXECUCOES, erraram, naoTerminaram,
                        fatias / EXECUCOES, transmissoes / EXECUCOES));
            }

        return correcao = List.copyOf(saida);
    }

    /* ===================== 2. numeracao ===================== */

    public record Numeracao(String protocolo, int espaco, int janela, int erraramOFluxo,
            int sacudida) {}

    /** Os espacos de numeracao medidos. */
    public static final int[] ESPACOS = {4, 8};

    private static List<Numeracao> numeracao;

    /**
     * A JANELA CONTRA O ESPACO DE NUMERACAO.
     *
     * <p>O numero no quadro e a posicao modulo o espaco, entao o receptor precisa adivinhar
     * de qual volta aquele numero e. A regra de bolso diz janela ate {@code espaco - 1} para
     * o volta N e ate {@code espaco / 2} para a repeticao seletiva.
     *
     * <p>Aqui a regra nao e citada: a tabela sobe a janela de um em um e o juiz diz em qual
     * valor o fluxo entregue para de bater. O erro nao e um travamento nem uma excecao: o
     * protocolo continua rodando, confirmando tudo, e entrega bytes trocados.
     */
    public static List<Numeracao> numeracao() {
        if (numeracao != null) return numeracao;

        List<Numeracao> saida = new ArrayList<>();

        for (String nome : nomes())
            for (int espaco : ESPACOS)
                for (int janela = 1; janela <= espaco; janela++)
                    saida.add(new Numeracao(nome, espaco, janela,
                            quantasErraram(nome, espaco, janela, Canal.EM_ORDEM),
                            Canal.EM_ORDEM));

        return numeracao = List.copyOf(saida);
    }

    private static int quantasErraram(String nome, int espaco, int janela, int sacudida) {
        int erraram = 0;

        for (int semente = 1; semente <= EXECUCOES_DA_NUMERACAO; semente++) {
            Protocolo.Resultado resultado =
                    rodar(novo(nome), espaco, janela, 0.2, sacudida, semente);

            if (!resultado.terminou() || !resultado.correto(fluxo())) erraram++;
        }

        return erraram;
    }

    /** A maior janela que entrega o fluxo certo em todas as execucoes. */
    public static int maiorJanelaQueFunciona(String nome, int espaco, int sacudida) {
        int maior = 0;

        for (int janela = 1; janela <= espaco; janela++) {
            if (quantasErraram(nome, espaco, janela, sacudida) > 0) break;

            maior = janela;
        }

        return maior;
    }

    /* ===================== 3. custo ===================== */

    public record Custo(String protocolo, double perda, double transmissoesPorByte,
            double retransmissoesPorPerda) {}

    private static List<Custo> custos;

    /**
     * QUANTO CADA PROTOCOLO GASTA COM PERDA.
     *
     * <p>A unidade e transmissao por byte entregue, nunca segundo. A segunda coluna e o que
     * a confirmacao acumulada esconde: quantos quadros o emissor manda de novo por quadro
     * que se perdeu de fato.
     */
    public static List<Custo> custos() {
        if (custos != null) return custos;

        List<Custo> saida = new ArrayList<>();

        for (String nome : nomes())
            for (double perda : PERDAS) {
                int espaco = 32;
                int janela = Math.min(8, novo(nome).maiorJanela(espaco));

                double transmissoes = 0;
                double retransmissoes = 0;
                double perdidos = 0;

                for (int semente = 1; semente <= EXECUCOES; semente++) {
                    Sorteio sorteio = new Sorteio(semente);

                    Canal ida = new Canal(ATRASO, perda, Canal.EM_ORDEM, sorteio);
                    Canal volta = new Canal(ATRASO, perda, Canal.EM_ORDEM, sorteio);

                    Protocolo.Resultado resultado = novo(nome).entregar(fluxo(), ida, volta,
                            espaco, janela, 2 * ATRASO + Canal.EM_ORDEM + 2, 200_000);

                    transmissoes += resultado.transmissoesPorByte();
                    retransmissoes += resultado.retransmissoes();
                    perdidos += ida.perdidos() + volta.perdidos();
                }

                saida.add(new Custo(nome, perda, transmissoes / EXECUCOES,
                        perdidos == 0 ? 0 : retransmissoes / perdidos));
            }

        return custos = List.copyOf(saida);
    }

    /* ===================== 4. enchendo o enlace ===================== */

    public record Enchimento(String protocolo, int janela, double uso, double formula) {}

    /** As janelas medidas no enchimento. */
    public static final int[] JANELAS = {1, 2, 4, 8, 10, 16};

    private static List<Enchimento> enchimento;

    /**
     * QUANTO DO ENLACE CADA JANELA ENCHE.
     *
     * <p>Com atraso de ida igual a cinco fatias, a ida e volta sao dez. Um quadro mandado na
     * fatia t tem a confirmacao de volta na fatia t mais dez, entao uma janela de tamanho W
     * mantem W quadros no ar a cada dez fatias: o uso e W sobre dez, ate encher.
     *
     * <p>A formula nao sai de implementacao nenhuma, e por isso ela e o segundo juiz. Depois
     * que o enlace enche, o que sobra de diferenca e a primeira ida e volta, que nenhuma
     * janela recupera: ela aparece como {@code n / (n + 10)}.
     */
    public static List<Enchimento> enchimento() {
        if (enchimento != null) return enchimento;

        List<Enchimento> saida = new ArrayList<>();

        for (String nome : nomes())
            for (int janela : JANELAS) {
                if (janela > novo(nome).maiorJanela(32)) continue;

                Protocolo.Resultado resultado =
                        rodar(novo(nome), 32, janela, 0, Canal.EM_ORDEM, 1);

                saida.add(new Enchimento(nome, janela, resultado.uso(), formula(janela)));
            }

        return enchimento = List.copyOf(saida);
    }

    public static double formula(int janela) {
        return Math.min(janela / (2.0 * ATRASO), TAMANHO / (double) (TAMANHO + 2 * ATRASO));
    }

    /* ===================== 5. desordem ===================== */

    public record Desordem(String protocolo, int espaco, int maiorEmOrdem,
            int maiorDesordenando, int regraDoLivro) {}

    private static List<Desordem> desordem;

    /**
     * A REGRA DO LIVRO SUPOE CANAL QUE PRESERVA A ORDEM.
     *
     * <p>Com entrega em ordem, o unico quadro atrasado que o receptor ve e uma
     * retransmissao, e o numero dela esta sempre atras. Com entrega fora de ordem, um quadro
     * NOVO pode chegar depois de outro mais novo ainda, e a conta de qual volta ele e muda.
     */
    public static List<Desordem> desordem() {
        if (desordem != null) return desordem;

        List<Desordem> saida = new ArrayList<>();

        for (String nome : nomes()) {
            int espaco = 8;

            saida.add(new Desordem(nome, espaco,
                    maiorJanelaQueFunciona(nome, espaco, Canal.EM_ORDEM),
                    maiorJanelaQueFunciona(nome, espaco, Canal.DESORDENA),
                    novo(nome).maiorJanela(espaco)));
        }

        return desordem = List.copyOf(saida);
    }
}
