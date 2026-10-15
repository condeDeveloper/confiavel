package br.com.conde.confiavel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** As pecas, uma a uma, antes de qualquer medida. */
class ConfiavelTest {

    // ───────────────────── o sorteio ─────────────────────

    @Nested
    @DisplayName("o sorteio da casa")
    class OSorteio {

        private long estado;

        private int proximoSemDeslocamento() {
            estado = estado * 6364136223846793005L + 1442695040888963407L;

            return (int) estado;
        }

        private int ateSemDeslocamento(int quantos) {
            return Math.floorMod(proximoSemDeslocamento(), quantos);
        }

        @Test
        @DisplayName("o sorteio da os mesmos numeros em qualquer maquina")
        void oSorteioEhFixo() {
            Sorteio sorteio = new Sorteio(1);

            assertThat(List.of(sorteio.proximo(), sorteio.proximo(), sorteio.proximo()))
                    .containsExactly(1817669548, -2107078989, -1510284903);
        }

        /**
         * A SACUDIDA DO CANAL E {@code ate(4)}, E QUATRO E POTENCIA DE DOIS.
         *
         * <p>Com os bits de baixo ela usaria os dois ultimos bits do estado, que percorrem
         * 0, 1, 2, 3 e voltam. O canal desordenaria sempre do mesmo jeito, e a repeticao
         * seletiva, que existe para lidar com chegada fora de ordem, seria testada contra um
         * padrao so disfarcado de milhares.
         */
        @Test
        @DisplayName("sem o deslocamento, a sacudida do canal vira um ciclo de quatro")
        void semODeslocamentoASacudidaEhUmCiclo() {
            estado = 1;

            List<Integer> saida = new ArrayList<>();

            for (int i = 0; i < 16; i++) saida.add(ateSemDeslocamento(Canal.DESORDENA));

            assertThat(saida.subList(0, 4))
                    .as("os quatro valores, cada um uma vez")
                    .containsExactlyInAnyOrder(0, 1, 2, 3);

            assertThat(saida.subList(4, 8)).isEqualTo(saida.subList(0, 4));
            assertThat(saida.subList(8, 12)).isEqualTo(saida.subList(0, 4));

            Set<String> comBits = new LinkedHashSet<>();

            Sorteio sorteio = new Sorteio(1);

            for (int bloco = 0; bloco < 200; bloco++) {
                StringBuilder quatro = new StringBuilder();

                for (int i = 0; i < 4; i++) quatro.append(sorteio.ate(Canal.DESORDENA));

                comBits.add(quatro.toString());
            }

            assertThat(comBits)
                    .as("com os bits de cima, os blocos de quatro sacudidas nao se repetem")
                    .hasSizeGreaterThan(100);
        }
    }

    // ───────────────────── o canal ─────────────────────

    @Nested
    @DisplayName("o canal")
    class OCanal {

        @Test
        @DisplayName("sem perda, o que entra sai, com o atraso combinado")
        void semPerdaTudoChega() {
            Canal canal = Canal.emOrdem(5, 0, new Sorteio(1));

            canal.mandar(Quadro.dado(0, (byte) 7), 0);

            assertThat(canal.receber(4)).isEmpty();
            assertThat(canal.receber(5)).containsExactly(Quadro.dado(0, (byte) 7));
            assertThat(canal.vazio()).isTrue();
        }

        @Test
        @DisplayName("com perda total, nada chega nunca")
        void comPerdaTotalNadaChega() {
            Canal canal = Canal.emOrdem(1, 1.0, new Sorteio(1));

            for (int i = 0; i < 50; i++) canal.mandar(Quadro.dado(i, (byte) i), i);

            assertThat(canal.receber(1000)).isEmpty();
            assertThat(canal.perdidos()).isEqualTo(50);
            assertThat(canal.entregues()).isZero();
        }

        @Test
        @DisplayName("o canal em ordem entrega na ordem em que recebeu")
        void oCanalEmOrdemPreservaAOrdem() {
            Canal canal = Canal.emOrdem(2, 0, new Sorteio(1));

            for (int i = 0; i < 5; i++) canal.mandar(Quadro.dado(i, (byte) i), i);

            List<Integer> chegaram = new ArrayList<>();

            for (int fatia = 0; fatia < 20; fatia++)
                for (Quadro quadro : canal.receber(fatia)) chegaram.add(quadro.numero());

            assertThat(chegaram).containsExactly(0, 1, 2, 3, 4);
        }

        @Test
        @DisplayName("o canal que desordena entrega fora de ordem, e isso e o ponto")
        void oCanalQueDesordenaDesordena() {
            Canal canal = Canal.desordenando(2, 0, new Sorteio(3));

            for (int i = 0; i < 40; i++) canal.mandar(Quadro.dado(i, (byte) i), i);

            List<Integer> chegaram = new ArrayList<>();

            for (int fatia = 0; fatia < 100; fatia++)
                for (Quadro quadro : canal.receber(fatia)) chegaram.add(quadro.numero());

            assertThat(chegaram).hasSize(40);

            boolean foraDeOrdem = false;

            for (int i = 1; i < chegaram.size(); i++)
                if (chegaram.get(i) < chegaram.get(i - 1)) foraDeOrdem = true;

            assertThat(foraDeOrdem).isTrue();
        }

        @Test
        @DisplayName("sacudida menor que uma fatia nao existe")
        void aSacudidaTemMinimo() {
            assertThatThrownBy(() -> new Canal(1, 0, 0, new Sorteio(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ───────────────────── os protocolos ─────────────────────

    @Nested
    @DisplayName("os tres protocolos")
    class OsProtocolos {

        @Test
        @DisplayName("sem perda nenhuma, cada byte e mandado exatamente uma vez")
        void semPerdaCadaByteVaiUmaVez() {
            for (String nome : Medidas.nomes()) {
                Protocolo.Resultado resultado = Medidas.rodar(Medidas.novo(nome), 8,
                        Medidas.novo(nome).maiorJanela(8), 0, Canal.EM_ORDEM, 1);

                assertThat(resultado.correto(Medidas.fluxo())).as(nome).isTrue();
                assertThat(resultado.transmissoes()).as(nome).isEqualTo(Medidas.TAMANHO);
                assertThat(resultado.retransmissoes()).as(nome).isZero();
            }
        }

        @Test
        @DisplayName("a parada e espera precisa de dois numeros, e so")
        void aParadaEEsperaPrecisaDeDois() {
            Protocolo.Resultado resultado =
                    Medidas.rodar(new ParadaEEspera(), 2, 1, 0.3, Canal.EM_ORDEM, 7);

            assertThat(resultado.terminou()).isTrue();
            assertThat(resultado.correto(Medidas.fluxo())).isTrue();
        }

        @Test
        @DisplayName("com um numero so ela nao distingue o repetido do proximo")
        void comUmNumeroSoElaSeConfunde() {
            Protocolo.Resultado resultado =
                    Medidas.rodar(new ParadaEEspera(), 1, 1, 0.3, Canal.EM_ORDEM, 7);

            assertThat(resultado.correto(Medidas.fluxo()))
                    .as("todo numero e zero: um quadro repetido vira byte novo")
                    .isFalse();
        }

        @Test
        @DisplayName("o volta N retransmite a janela inteira, a seletiva so o que faltou")
        void oVoltaNRetransmiteAJanela() {
            Protocolo.Resultado voltaN =
                    Medidas.rodar(new VoltaN(), 32, 8, 0.05, Canal.EM_ORDEM, 11);

            Protocolo.Resultado seletiva =
                    Medidas.rodar(new RepeticaoSeletiva(), 32, 8, 0.05, Canal.EM_ORDEM, 11);

            assertThat(voltaN.correto(Medidas.fluxo())).isTrue();
            assertThat(seletiva.correto(Medidas.fluxo())).isTrue();

            assertThat(voltaN.retransmissoes()).isGreaterThan(seletiva.retransmissoes());
        }

        @Test
        @DisplayName("quem entrega bytes a menos nao terminou, e o resultado diz isso")
        void quemNaoTerminaAvisa() {
            Sorteio sorteio = new Sorteio(1);

            Canal ida = new Canal(5, 0.5, Canal.EM_ORDEM, sorteio);
            Canal volta = new Canal(5, 0.5, Canal.EM_ORDEM, sorteio);

            Protocolo.Resultado resultado = new ParadaEEspera()
                    .entregar(Medidas.fluxo(), ida, volta, 8, 1, 12, 300);

            assertThat(resultado.terminou()).isFalse();
            assertThat(resultado.entregue().length).isLessThan(Medidas.TAMANHO);
        }

        @Test
        @DisplayName("o que chega e sempre um prefixo do que saiu, mesmo sem terminar")
        void oQueChegaEhSemprePrefixo() {
            for (String nome : Medidas.nomes())
                for (int semente = 1; semente <= 10; semente++) {
                    Sorteio sorteio = new Sorteio(semente);

                    Canal ida = new Canal(5, 0.3, Canal.EM_ORDEM, sorteio);
                    Canal volta = new Canal(5, 0.3, Canal.EM_ORDEM, sorteio);

                    Protocolo protocolo = Medidas.novo(nome);

                    Protocolo.Resultado resultado = protocolo.entregar(Medidas.fluxo(), ida,
                            volta, 8, protocolo.maiorJanela(8), 17, 400);

                    byte[] entregue = resultado.entregue();

                    assertThat(entregue.length).isLessThanOrEqualTo(Medidas.TAMANHO);

                    for (int i = 0; i < entregue.length; i++)
                        assertThat(entregue[i])
                                .as(nome + ", semente " + semente + ", byte " + i)
                                .isEqualTo(Medidas.fluxo()[i]);
                }
        }
    }

    // ───────────────────── a janela que da a volta ─────────────────────

    @Nested
    @DisplayName("a janela que da a volta no espaco")
    class AJanelaCircular {

        @Test
        @DisplayName("tres antes do inicio esta fora de uma janela de tres")
        void foraDaJanela() {
            assertThat(Protocolo.dentroDaJanela(5, 5, 3, 8)).isTrue();
            assertThat(Protocolo.dentroDaJanela(7, 5, 3, 8)).isTrue();
            assertThat(Protocolo.dentroDaJanela(0, 5, 3, 8)).isFalse();
            assertThat(Protocolo.dentroDaJanela(4, 5, 3, 8)).isFalse();
        }

        @Test
        @DisplayName("a janela que passa do fim volta pelo comeco")
        void aJanelaDaAVolta() {
            assertThat(Protocolo.dentroDaJanela(0, 6, 4, 8)).isTrue();
            assertThat(Protocolo.dentroDaJanela(1, 6, 4, 8)).isTrue();
            assertThat(Protocolo.dentroDaJanela(2, 6, 4, 8)).isFalse();
        }

        /**
         * A JANELA DE METADE DO ESPACO E A MAIOR QUE NAO ENCOSTA NELA MESMA.
         *
         * <p>Com espaco 8 e janela 4, a janela de recepcao que comeca em 4 cobre 4, 5, 6 e
         * 7, e a anterior cobria 0, 1, 2 e 3: nenhum numero esta nas duas. Com janela 5, o
         * numero 0 esta nas duas, e e por isso que o receptor confunde.
         */
        @Test
        @DisplayName("com janela ate a metade, duas janelas seguidas nao se tocam")
        void duasJanelasSeguidasNaoSeTocam() {
            int espaco = 8;

            for (int janela = 1; janela <= espaco; janela++) {
                boolean tocam = false;

                for (int numero = 0; numero < espaco; numero++)
                    if (Protocolo.dentroDaJanela(numero, 0, janela, espaco)
                            && Protocolo.dentroDaJanela(numero, janela, janela, espaco))
                        tocam = true;

                assertThat(tocam)
                        .as("janela " + janela)
                        .isEqualTo(janela > espaco / 2);
            }
        }
    }
}
