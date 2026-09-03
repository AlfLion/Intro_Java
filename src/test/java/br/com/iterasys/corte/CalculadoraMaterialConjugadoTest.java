package br.com.iterasys.corte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraMaterialConjugadoTest {

    private final Material poron = new Material("Poron", 1400);
    private final Material colaTransfer = new Material("Cola transfer", 1220);
    private final MaterialConjugado poronComCola = new MaterialConjugado(poron, colaTransfer);

    @Test
    void geraPlacasCheiasMaisTiraComplementarComSobra() {
        // peça de 200x100mm: cabem 84 por placa cheia (1400x1220); 262 peças = 3 placas + 10 restantes
        Peca peca = new Peca("Gaxeta", 200, 100, 262);

        PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(poronComCola, peca);

        assertEquals(3, plano.placasCheias());
        assertEquals(1400, plano.larguraPlacaCheiaMm());
        assertEquals(1220, plano.comprimentoPlacaCheiaMm());

        assertTrue(plano.temTiraComplementar());
        assertEquals(1220, plano.tiraComplementar().larguraMm());
        assertEquals(200, plano.tiraComplementar().comprimentoMm());
        assertEquals(10, plano.tiraComplementar().quantidadePecas());

        assertEquals(poron, plano.sobra().material());
        assertEquals(180, plano.sobra().larguraMm());
        assertEquals(200, plano.sobra().comprimentoMm());
    }

    @Test
    void quandoQuantidadeFechaExatoEmPlacasCheiasNaoHaTiraNemSobra() {
        Peca peca = new Peca("Gaxeta", 200, 100, 84 * 3);

        PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(poronComCola, peca);

        assertEquals(3, plano.placasCheias());
        assertFalse(plano.temTiraComplementar());
        assertNull(plano.tiraComplementar());
        assertNull(plano.sobra());
    }

    @Test
    void quandoQuantidadeNaoFechaUmaPlacaUsaApenasATira() {
        Peca peca = new Peca("Gaxeta", 200, 100, 10);

        PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(poronComCola, peca);

        assertEquals(0, plano.placasCheias());
        assertTrue(plano.temTiraComplementar());
        assertEquals(200, plano.tiraComplementar().comprimentoMm());
        assertEquals(180, plano.sobra().larguraMm());
    }

    @Test
    void giroNaPlacaPodeTornarAPlacaMaisEconomicaQueATira() {
        // peça 600x700mm: sem girar só cabe 1 fileira de profundidade na placa (700*2 > 1220),
        // capacidade 2/placa. Girada (700x600), cabem 2 fileiras (600*2 <= 1220), capacidade 4/placa
        // — 305mm de poron/peça, batendo os 350mm/peça da tira (sem giro, 2 por fileira x 700mm).
        Peca peca = new Peca("Suporte", 600, 700, 5);

        PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(poronComCola, peca);

        assertEquals(1, plano.placasCheias());
        assertTrue(plano.placaGirada());
        assertTrue(plano.temTiraComplementar());
        assertEquals(1, plano.tiraComplementar().quantidadePecas());
        assertEquals(180, plano.sobra().larguraMm());
    }

    @Test
    void quandoTiraGastaMenosMaterialQuePlacaMesmoComGiroPreferATira() {
        // peça 900x750mm: em qualquer orientação só cabe 1 peça por placa (1220mm de poron/peça),
        // enquanto a tira (sem girar) abre exatamente 750mm por peça — bem mais econômica.
        Peca peca = new Peca("Console", 900, 750, 5);

        PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(poronComCola, peca);

        assertEquals(0, plano.placasCheias());
        assertTrue(plano.temTiraComplementar());
        assertFalse(plano.tiraComplementar().pecaGirada());
        assertEquals(1220, plano.tiraComplementar().larguraMm());
        assertEquals(3750, plano.tiraComplementar().comprimentoMm());
        assertEquals(5, plano.tiraComplementar().quantidadePecas());
        assertEquals(180, plano.sobra().larguraMm());
        assertEquals(3750, plano.sobra().comprimentoMm());
    }

    @Test
    void pecaGrandeDemaisParaAmbosOsModosEmQualquerOrientacaoLancaExcecao() {
        // com largura > 1220 e comprimento > 1220, nenhuma rotação cabe na profundidade
        // da placa (1220mm) nem na largura da tira (1220mm) — realmente não cabe em nada.
        Peca peca = new Peca("Painel gigante", 1300, 1250, 1);

        assertThrows(IllegalArgumentException.class, () -> CalculadoraMaterialConjugado.calcular(poronComCola, peca));
    }

    @Test
    void materiaisComMesmaLarguraNaoFormamMaterialConjugado() {
        Material a = new Material("A", 1000);
        Material b = new Material("B", 1000);

        assertThrows(IllegalArgumentException.class, () -> new MaterialConjugado(a, b));
    }

    @Test
    void identificaMaterialMaisLargoIndependenteDaOrdemDosArgumentos() {
        MaterialConjugado invertido = new MaterialConjugado(colaTransfer, poron);

        assertEquals(poron, invertido.getMaterialMaisLargo());
        assertEquals(colaTransfer, invertido.getMaterialMaisEstreito());
        assertEquals(180, invertido.getLarguraSobraMm());
    }
}
