# confiavel

Transporte confiável do zero em Java 21: **a janela que passa da metade do espaço de números
entrega dado errado, e o juiz acha isso sozinho.**

Três protocolos, parada e espera, volta N e repetição seletiva, rodando sobre um canal que
perde, atrasa e desordena. O que eles prometem é uma coisa só: o fluxo que chega é exatamente
o fluxo que saiu, byte por byte e na mesma ordem.

## O juiz é a comparação com o original

Não é "quase todos os bytes" nem "os bytes certos em alguma ordem". O juiz guarda o vetor que
entrou e compara com o vetor que saiu, em toda execução, com perda de até quarenta por cento
aplicada a **cada quadro nos dois sentidos**.

Nenhuma propriedade interna do protocolo substitui isso. Ele pode estar perfeitamente coerente
consigo mesmo, confirmar tudo o que recebe, terminar dizendo que entregou tudo, e ter entregue
bytes trocados. É exatamente o que a segunda tabela mostra acontecendo.

```
$ mvn exec:java -Dexec.args="correcao"

  protocolo               perda    execucoes   fluxo errado   nao terminou       fatias   transmissoes
  parada e espera            0%           30              0              0         2001            200
  parada e espera           40%           30              0              0         6594            553
  volta N                    0%           30              0              0          294            200
  volta N                   40%           30              0              0         1964           1119
  repeticao seletiva         0%           30              0              0          504            200
  repeticao seletiva        40%           30              0              0         2705            554
```

Olhe a coluna das fatias **sem perda nenhuma**: a parada e espera gasta 2.001 fatias para
entregar o que o volta N entrega em 294. O protocolo correto mais simples é sete vezes mais
caro, e não por causa de perda: por causa de espera.

## A janela contra o espaço de numeração

O número que vai no quadro não é a posição do byte no fluxo: é a posição **módulo** o espaço de
numeração, porque o cabeçalho tem tamanho fixo. O receptor vê `3` e precisa decidir se aquele é
o terceiro byte, o décimo primeiro ou o centésimo trigésimo quinto.

A regra de bolso diz janela até `espaco - 1` para o volta N e até `espaco / 2` para a repetição
seletiva. Aqui a regra não é citada: a janela sobe de um em um e o juiz diz onde para de bater.

```
$ mvn exec:java -Dexec.args="numeracao"

  espaco de numeracao 8
  protocolo                 j1      j2      j3      j4      j5      j6      j7      j8
  parada e espera           ok      ok      ok      ok      ok      ok      ok      ok
  volta N                   ok      ok      ok      ok      ok      ok      ok  40 err
  repeticao seletiva        ok      ok      ok      ok  40 err  40 err  40 err  40 err
```

Quarenta execuções por casa. O limite medido é **exatamente** `espaco / 2` e `espaco - 1`, e a
passagem é abrupta: com janela 4 a repetição seletiva acerta as 40, com janela 5 ela erra as 40.

E o erro não é travamento nem exceção. O protocolo termina, achando que entregou tudo:

```java
Protocolo.Resultado resultado = Medidas.rodar(new RepeticaoSeletiva(), 8, 5, 0.2, ...);

resultado.terminou()               // true
resultado.correto(Medidas.fluxo()) // false
```

A razão é geométrica. Com espaço 8 e janela 4, a janela de recepção que começa em 4 cobre
`4,5,6,7` e a anterior cobria `0,1,2,3`: nenhum número está nas duas. Com janela 5, o número 0
está nas duas, e aí uma retransmissão do bloco anterior cai dentro da janela de agora e é
guardada como se fosse dado novo.

## O que a confirmação acumulada esconde

```
$ mvn exec:java -Dexec.args="custo"

  protocolo               perda  transmissoes por byte retransmitidos por perdido
  parada e espera            2%                  1.039                       1.00
  volta N                    2%                  1.157                       3.67
  repeticao seletiva         2%                  1.039                       1.00
  parada e espera           20%                  1.548                       1.00
  volta N                   20%                  3.007                       1.84
  repeticao seletiva        20%                  1.551                       1.00
```

A confirmação do volta N é acumulada: confirmar `n` quer dizer "recebi tudo até n". Isso o torna
imune a confirmação perdida, porque a próxima confirma as anteriores. O preço é que o emissor
não sabe **o que** faltou, só até onde chegou, e volta tudo: com 2% de perda ele manda de novo
3,67 quadros por quadro que se perdeu de fato.

A coluna cai conforme a perda sobe, e isso não é um erro: com perda alta, boa parte da janela já
ia se perder de qualquer jeito, então o reenvio desperdiça menos.

## A janela que o enlace comporta

```
$ mvn exec:java -Dexec.args="enchendo"

  protocolo                janela   uso medido      formula      razao
  parada e espera               1       0.1000       0.1000     0.9995
  volta N                       2       0.1996       0.2000     0.9980
  volta N                       4       0.3968       0.4000     0.9921
  volta N                       8       0.7752       0.8000     0.9690
  volta N                      10       0.9524       0.9524     1.0000
  volta N                      16       0.9524       0.9524     1.0000
```

A ida e volta é de 10 fatias, então uma janela de W quadros mantém W quadros no ar a cada 10
fatias: o uso é `W / 10`, até encher. A fórmula não sai de implementação nenhuma, e por isso ela
é o segundo juiz.

Depois que a janela chega a 10, dobrá-la não muda nada. Os 4,76% que faltam são a **primeira**
ida e volta, em que o canal está vazio e ninguém tem o que confirmar, e nenhuma janela paga por
isso: é `200 / 210`, exatamente.

## O que a entrega fora de ordem faz com a regra

```
$ mvn exec:java -Dexec.args="desordem"

  protocolo              regra do livro  medido em ordem medido desordenando
  parada e espera                     1                8                  8
  volta N                             7                7                  3
  repeticao seletiva                  4                4                  4
```

O canal desordena em até 3 fatias, que é menos que a ida e volta inteira. A parada e espera
manda um quadro de cada vez e não olha a janela, então a linha dela mede o espaço e não a
janela.

A maior janela segura do volta N cai de **7 para 3**. A da repetição seletiva não se mexe.

## Como rodar

```
mvn test
mvn exec:java -Dexec.args="tudo"
```

Os comandos do medidor são `correcao`, `numeracao`, `custo`, `enchendo` e `desordem`.

## O que as medidas me corrigiram

**Eu ia escrever que a regra da janela é uma propriedade do protocolo. Ela é uma propriedade do
protocolo MAIS o canal.** Com entrega em ordem, o volta N vai até `espaco - 1` e a repetição
seletiva até `espaco / 2`, exatamente como está nos livros, e a medida confirma os dois. Com um
canal que desordena em três fatias, o volta N cai para 3 e a seletiva continua em 4: **o
protocolo com a regra mais apertada é o que aguenta o canal mais feio.** A primeira versão deste
repositório tinha só o canal que desordena, e eu quase escrevi que a regra do livro estava
errada.

**A repetição seletiva não é "o volta N com mais memória".** Elas gastam o mesmo sem perda e a
diferença aparece inteira na perda: 3,007 transmissões por byte contra 1,551 com 20% de perda. A
memória do receptor não é o preço da seletiva, é o que ela compra.

**A parada e espera não é cara por causa de retransmissão.** Ela gasta praticamente as mesmas
transmissões por byte que a seletiva com 20% de perda, 1,548 contra 1,551, porque as duas mandam de novo só o
que faltou. O que ela gasta a mais é TEMPO: 3.424 fatias contra 1.277. Confundir as duas contas
é confundir eficiência de banda com uso do enlace.

**O limite do enlace cheio não é a janela, é a primeira ida e volta.** Eu esperava que o uso
chegasse a 1 com janela suficiente. Ele para em 0,9524, e esse número não é ruído: é
`200 / (200 + 10)`, o fluxo inteiro dividido pelo fluxo mais a ida e volta que ninguém recupera.
Com fluxo maior ele sobe; com fluxo de um byte, nenhuma janela ajuda em nada.

## O que não tem aqui

**Controle de congestionamento.** A janela daqui é fixa e dada. Descobrir sozinho qual janela o
caminho aguenta, sem ninguém dizer, é o assunto de
[`congestionamento`](https://github.com/condeDeveloper/congestionamento).

**Tempo de espera adaptativo.** O tempo limite daqui é constante e calculado da ida e volta
conhecida. Num caminho de verdade a ida e volta varia, e estimá-la com média móvel e desvio é o
que decide entre retransmitir cedo demais e esperar demais.

**Conexão.** Não há aperto de mão, não há fechamento, não há número inicial de sequência
sorteado. Os dois lados daqui já começam concordando em tudo, e é justamente o que um
estabelecimento de conexão existe para construir.

**Confirmação negativa e seletiva.** O receptor daqui confirma o que chegou. Dizer o que NÃO
chegou, que é o que o campo de confirmação seletiva do TCP faz, encurta a espera e é outro
desenho de cabeçalho.

**Corrupção.** O canal daqui perde o quadro inteiro ou entrega o quadro inteiro. Bit trocado que
chega parecendo válido é o assunto da soma de verificação, e está em
[`crc`](https://github.com/condeDeveloper/crc).

## Licença

MIT.
