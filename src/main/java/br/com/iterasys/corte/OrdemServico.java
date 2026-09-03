package br.com.iterasys.corte;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordem de serviço: um conjunto de peças demandadas, cada uma com o material
 * (único ou conjugado) que ela precisa.
 */
public class OrdemServico {

    private final String numero;
    private final List<ItemOrdemServico> itens = new ArrayList<>();

    public OrdemServico(String numero) {
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("número da ordem de serviço não pode ser vazio");
        }
        this.numero = numero;
    }

    public void adicionarItem(ItemOrdemServico item) {
        if (item == null) {
            throw new IllegalArgumentException("item não pode ser nulo");
        }
        itens.add(item);
    }

    public String getNumero() {
        return numero;
    }

    public List<ItemOrdemServico> getItens() {
        return List.copyOf(itens);
    }
}
