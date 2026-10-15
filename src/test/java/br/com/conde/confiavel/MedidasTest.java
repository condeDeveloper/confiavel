package br.com.conde.confiavel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TODO NUMERO DO README ESTA AQUI.
 *
 * <p>Texto nao quebra quando deixa de ser verdade. Teste quebra. Se alguma medida mudar,
 * este arquivo e a tabela do README caem juntos, e nenhum numero fica no texto sozinho.
 */
class MedidasTest {

    private static final List<Medidas.Correcao> CORRECAO = Medidas.correcao();
    private static final List<Medidas.Numeracao> NUMERACAO = Medidas.numeracao();
    private static final List<Medidas.Custo> CUSTOS = Medidas.custos();
    private static final List<Medidas.Enchimento> ENCHIMENTO = Medidas.enchimento();
    private static final List<Medidas.Desordem> DESORDEM = Medidas.desordem();

    private static Medidas.Correcao correcao(String protocolo, double perda) {
        return CORRECAO.stream()
                .filter(linha -> linha.protocolo().equals(protocolo))
                .filter(linha -> Math.abs(linha.perda() - perda) < 1e-9)
                .findFirst().orElseThrow();
    }

    private static int erros(String protocolo, int espaco, int janela) {
        return NUMERACAO.stream()
                .filter(linha -> linha.protocolo().equals(protocolo))
                .filter(linha -> linha.espaco() == espaco)
                .filter(linha -> linha.janela() == janela)
                .findFirst().orElseThrow().erraramOFluxo();
    }

    private static Medidas.Custo custo(String protocolo, double perda) {
        return CUSTOS.stream()
                .filter(linha -> linha.protocolo().equals(protocolo))
                .filter(linha -> Math.abs(linha.perda() - perda) < 1e-9)
                .findFirst().orElseThrow();
    }

    private static Medidas.Enchimento enchimento(String protocolo, int janela) {
        return ENCHIMENTO.stream()
                .filter(linha -> linha.protocolo().equals(protocolo))
                .filter(linha -> linha.janela() == janela)
                .findFirst().orElseThrow();
    }

    private static Medidas.Desordem desordem(String protocolo) {
        return DESORDEM.stream().filter(linha -> linha.protocolo().equals(protocolo))
                .findFirst().orElseThrow();
    }

    // ───────────────────── 1. correcao ─────────────────────

    @Nested
    @DisplayName("tabela 1: o fluxo que chega e exatamente o que saiu")
    class ACorrecao {

        @Test
        @DisplayName("nenhuma das 360 execucoes entregou fluxo errado ou ficou pelo caminho")
        void ninguemErrou() {
            assertThat(CORRECAO).hasSize(12);

            for (Medidas.Correcao linha : CORRECAO) {
                assertThat(linha.execucoes()).isEqualTo(30);
                assertThat(linha.erraramOFluxo())
                        .as(linha.protocolo() + " com perda " + linha.perda())
                        .isZero();
                assertThat(linha.naoTerminaram()).isZero();
            }
        }

        @Test
        @DisplayName("sem perda, 200 transmissoes para 200 bytes, nos tres")
        void semPerdaNaoHaRetransmissao() {
            for (String nome : Medidas.nomes())
                assertThat(correcao(nome, 0).transmissoes()).as(nome).isEqualTo(200);
        }

        /**
         * O MESMO FLUXO, O MESMO CANAL, E TRES CONTAS DE FATIAS BEM DIFERENTES.
         *
         * <p>Sem perda nenhuma, a parada e espera gasta 2.001 fatias para entregar o que o
         * volta N entrega em 294. O protocolo correto mais simples e tambem o mais caro por
         * uma ordem de grandeza, e nao por causa de perda: por causa de espera.
         */
        @Test
        @DisplayName("sem perda, parada e espera gasta 2.001 fatias e o volta N gasta 294")
        void aEsperaCustaUmaOrdemDeGrandeza() {
            assertThat(correcao("parada e espera", 0).fatias()).isEqualTo(2001);
            assertThat(correcao("volta N", 0).fatias()).isEqualTo(294);
            assertThat(correcao("repeticao seletiva", 0).fatias()).isEqualTo(504);
        }

        @Test
        @DisplayName("com 40% de perda nos dois sentidos, tudo ainda chega certo")
        void comQuarentaPorCentoTudoChega() {
            for (String nome : Medidas.nomes()) {
                assertThat(correcao(nome, 0.4).erraramOFluxo()).as(nome).isZero();
                assertThat(correcao(nome, 0.4).naoTerminaram()).as(nome).isZero();
            }

            assertThat(correcao("volta N", 0.4).transmissoes())
                    .as("e o volta N paga 1.119 transmissoes por 200 bytes")
                    .isEqualTo(1119);
        }
    }

    // ───────────────────── 2. numeracao ─────────────────────

    @Nested
    @DisplayName("tabela 2: a janela contra o espaco de numeracao")
    class ANumeracao {

        /**
         * A REGRA NAO E CITADA: ELA E MEDIDA.
         *
         * <p>Com espaco 8, a repeticao seletiva entrega o fluxo certo com janela ate 4 e
         * erra em todas as 40 execucoes com janela 5. O volta N vai ate 7 e erra com 8. Os
         * dois numeros sao exatamente {@code espaco / 2} e {@code espaco - 1}.
         */
        @Test
        @DisplayName("com espaco 8, a seletiva para na janela 4 e o volta N na 7")
        void osDoisLimitesComEspacoOito() {
            for (int janela = 1; janela <= 4; janela++)
                assertThat(erros("repeticao seletiva", 8, janela))
                        .as("seletiva, janela " + janela)
                        .isZero();

            assertThat(erros("repeticao seletiva", 8, 5)).isEqualTo(40);

            for (int janela = 1; janela <= 7; janela++)
                assertThat(erros("volta N", 8, janela))
                        .as("volta N, janela " + janela)
                        .isZero();

            assertThat(erros("volta N", 8, 8)).isEqualTo(40);
        }

        @Test
        @DisplayName("com espaco 4 a conta e a mesma: 2 para a seletiva e 3 para o volta N")
        void osDoisLimitesComEspacoQuatro() {
            assertThat(erros("repeticao seletiva", 4, 2)).isZero();
            assertThat(erros("repeticao seletiva", 4, 3)).isEqualTo(40);

            assertThat(erros("volta N", 4, 3)).isZero();
            assertThat(erros("volta N", 4, 4)).isEqualTo(40);
        }

        @Test
        @DisplayName("o limite medido e o que cada protocolo promete")
        void oLimiteMedidoEhOProMetido() {
            for (int espaco : Medidas.ESPACOS) {
                assertThat(Medidas.maiorJanelaQueFunciona("repeticao seletiva", espaco,
                        Canal.EM_ORDEM))
                        .as("seletiva, espaco " + espaco)
                        .isEqualTo(espaco / 2);

                assertThat(Medidas.maiorJanelaQueFunciona("volta N", espaco, Canal.EM_ORDEM))
                        .as("volta N, espaco " + espaco)
                        .isEqualTo(espaco - 1);
            }
        }

        @Test
        @DisplayName("e o erro nao e travamento: o protocolo entrega byte trocado e segue")
        void oErroNaoEhTravamento() {
            Protocolo.Resultado resultado =
                    Medidas.rodar(new RepeticaoSeletiva(), 8, 5, 0.2, Canal.EM_ORDEM, 1);

            assertThat(resultado.terminou())
                    .as("ele acha que entregou tudo")
                    .isTrue();

            assertThat(resultado.correto(Medidas.fluxo()))
                    .as("e o que chegou nao e o que saiu")
                    .isFalse();
        }
    }

    // ───────────────────── 3. custo ─────────────────────

    @Nested
    @DisplayName("tabela 3: quanto cada protocolo gasta com perda")
    class OCusto {

        @Test
        @DisplayName("sem perda os tres gastam uma transmissao por byte, exatamente")
        void semPerdaUmaPorByte() {
            for (String nome : Medidas.nomes())
                assertThat(custo(nome, 0).transmissoesPorByte())
                        .as(nome)
                        .isCloseTo(1.0, within(1e-9));
        }

        /**
         * O QUE A CONFIRMACAO ACUMULADA ESCONDE.
         *
         * <p>Com 2% de perda, o volta N manda de novo 3,67 quadros para cada quadro que se
         * perdeu de fato. A confirmacao acumulada e comoda e torna o protocolo imune a
         * confirmacao perdida; o preco e que o emissor nao sabe O QUE faltou, so ate onde
         * chegou, e volta tudo.
         */
        @Test
        @DisplayName("com 2% de perda, o volta N remanda 3,67 quadros por quadro perdido")
        void oVoltaNRemandaDemais() {
            assertThat(custo("volta N", 0.02).retransmissoesPorPerda())
                    .isCloseTo(3.67, within(0.01));

            assertThat(custo("repeticao seletiva", 0.02).retransmissoesPorPerda())
                    .as("a seletiva remanda exatamente um por um")
                    .isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("e quanto maior a perda, MENOR esse desperdicio relativo")
        void oDesperdicioRelativoCai() {
            assertThat(custo("volta N", 0.3).retransmissoesPorPerda())
                    .isLessThan(custo("volta N", 0.02).retransmissoesPorPerda());

            assertThat(custo("volta N", 0.3).retransmissoesPorPerda())
                    .isCloseTo(1.51, within(0.01));
        }

        @Test
        @DisplayName("com 20% de perda o volta N gasta 3,007 por byte e a seletiva 1,551")
        void aContaComVintePorCento() {
            assertThat(custo("volta N", 0.2).transmissoesPorByte())
                    .isCloseTo(3.007, within(0.001));

            assertThat(custo("repeticao seletiva", 0.2).transmissoesPorByte())
                    .isCloseTo(1.551, within(0.001));

            assertThat(custo("parada e espera", 0.2).transmissoesPorByte())
                    .as("a parada e espera gasta o mesmo que a seletiva, e demora o triplo")
                    .isCloseTo(1.548, within(0.001));
        }
    }

    // ───────────────────── 4. enchimento ─────────────────────

    @Nested
    @DisplayName("tabela 4: a janela que o enlace comporta")
    class OEnchimento {

        @Test
        @DisplayName("com janela 1 o uso e um decimo, que e uma fatia por ida e volta")
        void comJanelaUmOUsoEhUmDecimo() {
            assertThat(enchimento("parada e espera", 1).uso())
                    .isCloseTo(0.1, within(0.001));
        }

        @Test
        @DisplayName("o uso medido bate com a formula a menos de 4% em toda janela")
        void aFormulaBate() {
            for (Medidas.Enchimento linha : ENCHIMENTO)
                assertThat(linha.uso() / linha.formula())
                        .as(linha.protocolo() + ", janela " + linha.janela())
                        .isBetween(0.96, 1.01);
        }

        @Test
        @DisplayName("a janela que enche o enlace e a ida e volta inteira: 10")
        void aJanelaQueEnche() {
            assertThat(enchimento("volta N", 10).uso()).isCloseTo(0.9524, within(0.0001));

            assertThat(enchimento("volta N", 16).uso())
                    .as("dobrar a janela depois disso nao muda nada")
                    .isCloseTo(enchimento("volta N", 10).uso(), within(1e-9));
        }

        @Test
        @DisplayName("os 4,76% que faltam sao a primeira ida e volta, e nenhuma janela paga")
        void oQueFaltaEhAPrimeiraIdaEVolta() {
            assertThat(enchimento("volta N", 16).uso())
                    .isCloseTo(Medidas.TAMANHO / (double) (Medidas.TAMANHO + 2 * Medidas.ATRASO),
                            within(0.001));
        }
    }

    // ───────────────────── 5. desordem ─────────────────────

    @Nested
    @DisplayName("tabela 5: o que a entrega fora de ordem faz com a regra")
    class ADesordem {

        @Test
        @DisplayName("com canal em ordem, o medido e exatamente a regra do livro")
        void emOrdemAregraVale() {
            assertThat(desordem("volta N").maiorEmOrdem())
                    .isEqualTo(desordem("volta N").regraDoLivro());

            assertThat(desordem("repeticao seletiva").maiorEmOrdem())
                    .isEqualTo(desordem("repeticao seletiva").regraDoLivro());
        }

        /**
         * A REGRA DO VOLTA N SUPOE CANAL QUE PRESERVA A ORDEM.
         *
         * <p>Com desordem de ate tres fatias, que e menos que a ida e volta, a maior janela
         * segura do volta N cai de 7 para 3. A da repeticao seletiva, que ja era a metade do
         * espaco, nao se mexe: o protocolo com a regra mais apertada e o que aguenta o canal
         * mais feio.
         */
        @Test
        @DisplayName("com canal que desordena, o volta N cai de 7 para 3 e a seletiva fica em 4")
        void desordenandoAregraCai() {
            assertThat(desordem("volta N").maiorDesordenando()).isEqualTo(3);

            assertThat(desordem("repeticao seletiva").maiorDesordenando())
                    .isEqualTo(desordem("repeticao seletiva").maiorEmOrdem())
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("e com isso a seletiva aguenta janela maior que o volta N")
        void aSeletivaPassaAFrente() {
            assertThat(desordem("repeticao seletiva").maiorDesordenando())
                    .isGreaterThan(desordem("volta N").maiorDesordenando());
        }
    }
}
