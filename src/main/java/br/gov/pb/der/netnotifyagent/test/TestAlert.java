package br.gov.pb.der.netnotifyagent.test;

import br.gov.pb.der.netnotifyagent.ui.Alert;

public class TestAlert {
    public static void main(String[] args) {
        System.out.println("Testing Alert.showHtml...");
        
        String testJson = "{'content':'<h2 class=\"ql-align-center\"><strong>teste</strong><img src=\"https://picsum.photos/id/237/200/300\"></h2>','level':'Alto','type':'Notificação','user':'admin','createdAt':'29-09-2025 15:05:00','updatedAt':'29-09-2025 15:05:00'}";
        
        Alert alert = Alert.getInstance();
        alert.showHtml(testJson);
        
        System.out.println("Test completed.");
    }
}